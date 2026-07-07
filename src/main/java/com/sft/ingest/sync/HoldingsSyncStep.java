package com.sft.ingest.sync;

import com.plaid.client.model.Holding;
import com.plaid.client.model.InvestmentsHoldingsGetRequest;
import com.plaid.client.model.InvestmentsHoldingsGetResponse;
import com.plaid.client.model.Security;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import retrofit2.Response;

/** /investments/holdings/get - securities upserted before holdings (FK ordering), same transaction. */
public final class HoldingsSyncStep {

    public record Counts(int holdings, int securities) {}

    private static final String UPSERT_HOLDING = """
        INSERT OR REPLACE INTO holdings
          (account_id, security_id, as_of, quantity, cost_basis,
           institution_value, institution_price, iso_currency)
        VALUES (?,?,?,?,?,?,?,?)
        """;

    public Counts sync(PlaidApi client, Path dbPath, String accessToken, String asOf) throws IOException, SQLException {
        InvestmentsHoldingsGetRequest request = new InvestmentsHoldingsGetRequest().accessToken(accessToken);
        Response<InvestmentsHoldingsGetResponse> response = client.investmentsHoldingsGet(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("investmentsHoldingsGet failed: HTTP " + response.code());
        }
        List<Security> securities = response.body().getSecurities();
        List<Holding> holdings = response.body().getHoldings();

        IngestConnection.transact(dbPath, conn -> {
            SecuritiesUpserter.upsert(conn, securities);
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_HOLDING)) {
                for (Holding h : holdings) {
                    ps.setString(1, h.getAccountId());
                    ps.setString(2, h.getSecurityId());
                    ps.setString(3, asOf);
                    setNullableDouble(ps, 4, h.getQuantity());
                    setNullableDouble(ps, 5, h.getCostBasis());
                    setNullableDouble(ps, 6, h.getInstitutionValue());
                    setNullableDouble(ps, 7, h.getInstitutionPrice());
                    ps.setString(8, h.getIsoCurrencyCode());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });

        return new Counts(holdings.size(), securities.size());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
