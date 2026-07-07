package com.sft.ingest.link;

import com.google.gson.JsonObject;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import com.sft.ingest.sync.PlaidJson;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

/**
 * /exchange route: public_token -> access_token, enumerate accounts, write
 * items+accounts rows. Preserves the known INSERT OR REPLACE quirk on items
 * (re-linking an existing item_id resets cursor/last_synced_at to NULL,
 * since those columns aren't in this INSERT's column list) as-is - a
 * deliberate carryover from the Python source, not silently fixed here.
 */
final class ExchangeHandler {

    private static final String UPSERT_ITEM = """
        INSERT OR REPLACE INTO items (item_id, institution_id, institution_name, access_token, products)
        VALUES (?,?,?,?,?)
        """;
    private static final String UPSERT_ACCOUNT = """
        INSERT OR REPLACE INTO accounts (account_id, item_id, name, official_name, type, subtype, mask)
        VALUES (?,?,?,?,?,?,?)
        """;

    static void handle(HttpExchange exchange, PlaidApi client, Path dbPath) throws IOException {
        try {
            JsonObject body;
            try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                body = PlaidJson.gson().fromJson(reader, JsonObject.class);
            }
            String publicToken = getString(body, "public_token");
            String institutionId = getString(body, "institution_id");
            String institutionNameRaw = getString(body, "institution_name");
            String institutionName = institutionNameRaw != null ? institutionNameRaw : "Unknown";
            String flowRaw = getString(body, "flow");
            String flow = flowRaw != null ? flowRaw : "bank";
            String grantedRaw = getString(body, "products");
            String granted = (grantedRaw == null || grantedRaw.isBlank()) ? fallbackProducts(flow) : grantedRaw;

            ItemPublicTokenExchangeRequest exchangeReq = new ItemPublicTokenExchangeRequest().publicToken(publicToken);
            Response<ItemPublicTokenExchangeResponse> exchangeResp = client.itemPublicTokenExchange(exchangeReq).execute();
            if (!exchangeResp.isSuccessful()) {
                HttpResponses.sendJson(exchange, 500, error("token exchange failed: HTTP " + exchangeResp.code()));
                return;
            }
            String accessToken = exchangeResp.body().getAccessToken();
            String itemId = exchangeResp.body().getItemId();

            AccountsGetRequest accountsReq = new AccountsGetRequest().accessToken(accessToken);
            Response<AccountsGetResponse> accountsResp = client.accountsGet(accountsReq).execute();
            if (!accountsResp.isSuccessful()) {
                HttpResponses.sendJson(exchange, 500, error("accounts_get failed: HTTP " + accountsResp.code()));
                return;
            }
            List<AccountBase> accounts = accountsResp.body().getAccounts();

            IngestConnection.transact(dbPath, conn -> {
                try (PreparedStatement ps = conn.prepareStatement(UPSERT_ITEM)) {
                    ps.setString(1, itemId);
                    ps.setString(2, institutionId);
                    ps.setString(3, institutionName);
                    ps.setString(4, accessToken);
                    ps.setString(5, granted);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(UPSERT_ACCOUNT)) {
                    for (AccountBase acct : accounts) {
                        ps.setString(1, acct.getAccountId());
                        ps.setString(2, itemId);
                        ps.setString(3, acct.getName());
                        ps.setString(4, acct.getOfficialName());
                        ps.setString(5, acct.getType() != null ? acct.getType().toString() : null);
                        ps.setString(6, acct.getSubtype() != null ? acct.getSubtype().toString() : null);
                        ps.setString(7, acct.getMask());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return null;
            });

            JsonObject result = new JsonObject();
            result.addProperty("institution_name", institutionName);
            result.addProperty("account_count", accounts.size());
            result.addProperty("products", granted);
            HttpResponses.sendJson(exchange, 200, result);
        } catch (Exception e) {
            HttpResponses.sendJson(exchange, 500, error(e.getMessage()));
        }
    }

    private static String fallbackProducts(String flow) {
        LinkFlow spec = LinkFlow.ALL.getOrDefault(flow, LinkFlow.ALL.get("bank"));
        List<String> all = new ArrayList<>(spec.products());
        all.addAll(spec.requiredIfSupported());
        return String.join(",", all);
    }

    private static String getString(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private static JsonObject error(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return obj;
    }

    private ExchangeHandler() {}
}
