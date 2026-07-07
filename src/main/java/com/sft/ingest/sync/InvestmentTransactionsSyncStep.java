package com.sft.ingest.sync;

import com.plaid.client.model.InvestmentTransaction;
import com.plaid.client.model.InvestmentsTransactionsGetRequest;
import com.plaid.client.model.InvestmentsTransactionsGetRequestOptions;
import com.plaid.client.model.InvestmentsTransactionsGetResponse;
import com.plaid.client.model.Security;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import retrofit2.Response;

/**
 * /investments/transactions/get - offset-paginated, page size 500 (Plaid's
 * documented max). Re-upserts securities too: sold/historical positions can
 * appear here but not in current holdings, and skipping this would violate
 * the FK on security_id.
 */
public final class InvestmentTransactionsSyncStep {

    private static final int PAGE_SIZE = 500;

    private static final String UPSERT = """
        INSERT OR REPLACE INTO investment_transactions
          (investment_transaction_id, account_id, security_id, date, type, subtype,
           amount, quantity, price, fees, iso_currency, raw_json)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;

    public int sync(PlaidApi client, Path dbPath, String accessToken, LocalDate start, LocalDate end)
        throws IOException, SQLException {
        int total = 0;
        int offset = 0;
        while (true) {
            InvestmentsTransactionsGetRequestOptions options = new InvestmentsTransactionsGetRequestOptions()
                .count(PAGE_SIZE)
                .offset(offset);
            InvestmentsTransactionsGetRequest request = new InvestmentsTransactionsGetRequest()
                .accessToken(accessToken)
                .startDate(start)
                .endDate(end)
                .options(options);
            Response<InvestmentsTransactionsGetResponse> response = client.investmentsTransactionsGet(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("investmentsTransactionsGet failed: HTTP " + response.code());
            }
            InvestmentsTransactionsGetResponse body = response.body();
            List<Security> securities = body.getSecurities();
            List<InvestmentTransaction> txs = body.getInvestmentTransactions();

            IngestConnection.transact(dbPath, conn -> {
                SecuritiesUpserter.upsert(conn, securities);
                try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                    for (InvestmentTransaction tx : txs) {
                        ps.setString(1, tx.getInvestmentTransactionId());
                        ps.setString(2, tx.getAccountId());
                        ps.setString(3, tx.getSecurityId());
                        ps.setString(4, tx.getDate() != null ? tx.getDate().toString() : null);
                        ps.setString(5, tx.getType() != null ? tx.getType().toString() : null);
                        ps.setString(6, tx.getSubtype() != null ? tx.getSubtype().toString() : null);
                        setNullableDouble(ps, 7, tx.getAmount());
                        setNullableDouble(ps, 8, tx.getQuantity());
                        setNullableDouble(ps, 9, tx.getPrice());
                        setNullableDouble(ps, 10, tx.getFees());
                        ps.setString(11, tx.getIsoCurrencyCode());
                        ps.setString(12, PlaidJson.dump(tx));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return null;
            });

            total += txs.size();
            offset += txs.size();
            Integer totalCount = body.getTotalInvestmentTransactions();
            if (txs.isEmpty() || (totalCount != null && offset >= totalCount)) {
                break;
            }
        }
        return total;
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
