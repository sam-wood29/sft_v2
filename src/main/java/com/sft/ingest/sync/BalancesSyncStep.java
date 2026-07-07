package com.sft.ingest.sync;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountBalance;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import retrofit2.Response;

/**
 * /accounts/get (not the dedicated /accounts/balance/get - Plaid's Trial plan
 * rejects that endpoint with INVALID_PRODUCT: balance). Runs unconditionally
 * for every item regardless of granted products.
 */
public final class BalancesSyncStep {

    private static final Set<String> ELIGIBLE_TYPES = Set.of("depository", "credit");

    private static final String UPSERT = """
        INSERT OR REPLACE INTO account_balances
          (account_id, as_of, current, available, iso_currency)
        VALUES (?,?,?,?,?)
        """;

    public int sync(PlaidApi client, Path dbPath, String accessToken, String asOf) throws IOException, SQLException {
        AccountsGetRequest request = new AccountsGetRequest().accessToken(accessToken);
        Response<AccountsGetResponse> response = client.accountsGet(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("accountsGet failed: HTTP " + response.code());
        }
        List<AccountBase> accounts = response.body().getAccounts();

        return IngestConnection.transact(dbPath, conn -> {
            int rows = 0;
            try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                for (AccountBase acct : accounts) {
                    String type = acct.getType() != null ? acct.getType().toString().toLowerCase() : "";
                    if (!ELIGIBLE_TYPES.contains(type)) {
                        continue;
                    }
                    AccountBalance bal = acct.getBalances();
                    ps.setString(1, acct.getAccountId());
                    ps.setString(2, asOf);
                    setNullableDouble(ps, 3, bal.getCurrent());
                    setNullableDouble(ps, 4, bal.getAvailable());
                    ps.setString(5, bal.getIsoCurrencyCode());
                    ps.addBatch();
                    rows++;
                }
                ps.executeBatch();
            }
            return rows;
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
