package com.sft.ingest.sync;

import com.plaid.client.model.CreditCardLiability;
import com.plaid.client.model.LiabilitiesGetRequest;
import com.plaid.client.model.LiabilitiesGetResponse;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import retrofit2.Response;

/**
 * /liabilities/get - only credit liabilities are modeled (student/mortgage
 * arrays exist in Plaid's response but are out of scope here, matching the
 * Python source).
 */
public final class LiabilitiesSyncStep {

    private static final String UPSERT = """
        INSERT OR REPLACE INTO liabilities_credit
          (account_id, as_of, last_payment_amount, last_payment_date,
           last_statement_balance, last_statement_issue_date,
           minimum_payment_amount, next_payment_due_date, aprs_json, raw_json)
        VALUES (?,?,?,?,?,?,?,?,?,?)
        """;

    public int sync(PlaidApi client, Path dbPath, String accessToken, String asOf) throws IOException, SQLException {
        LiabilitiesGetRequest request = new LiabilitiesGetRequest().accessToken(accessToken);
        Response<LiabilitiesGetResponse> response = client.liabilitiesGet(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("liabilitiesGet failed: HTTP " + response.code());
        }
        List<CreditCardLiability> credits = response.body().getLiabilities().getCredit();
        List<CreditCardLiability> finalCredits = credits != null ? credits : List.of();

        return IngestConnection.transact(dbPath, conn -> {
            int n = 0;
            try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                for (CreditCardLiability c : finalCredits) {
                    ps.setString(1, c.getAccountId());
                    ps.setString(2, asOf);
                    setNullableDouble(ps, 3, c.getLastPaymentAmount());
                    ps.setString(4, c.getLastPaymentDate() != null ? c.getLastPaymentDate().toString() : null);
                    setNullableDouble(ps, 5, c.getLastStatementBalance());
                    ps.setString(
                        6,
                        c.getLastStatementIssueDate() != null ? c.getLastStatementIssueDate().toString() : null
                    );
                    setNullableDouble(ps, 7, c.getMinimumPaymentAmount());
                    ps.setString(8, c.getNextPaymentDueDate() != null ? c.getNextPaymentDueDate().toString() : null);
                    ps.setString(9, PlaidJson.dump(c.getAprs()));
                    ps.setString(10, PlaidJson.dump(c));
                    ps.addBatch();
                    n++;
                }
                ps.executeBatch();
            }
            return n;
        });
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
