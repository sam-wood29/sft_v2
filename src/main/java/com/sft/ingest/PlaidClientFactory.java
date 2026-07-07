package com.sft.ingest;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.config.PlaidConfig;
import java.util.HashMap;

/**
 * Shared Plaid client construction - the Python source had this duplicated
 * identically in sync.py and link.py; here it's one place.
 */
public final class PlaidClientFactory {

    public static PlaidApi create(PlaidConfig config) {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", config.clientId());
        apiKeys.put("secret", config.secret());
        ApiClient apiClient = new ApiClient(apiKeys);
        // Only literal "sandbox" uses the sandbox host - Plaid retired the
        // separate Development environment, so everything else (including
        // unset or "development") must map to Production.
        apiClient.setPlaidAdapter(config.isSandbox() ? ApiClient.Sandbox : ApiClient.Production);
        return apiClient.createService(PlaidApi.class);
    }

    private PlaidClientFactory() {}
}
