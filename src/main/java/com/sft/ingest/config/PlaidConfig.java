package com.sft.ingest.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Plaid credentials/config. Precedence per key: system property -> env var ->
 * .env file (sft_v2/.env) -> default. Deliberately does NOT read
 * FINANCE_DB_PATH/SFT_DB_PATH - com.sft.data.DbConfig is the one source of
 * truth for where the database lives.
 */
public final class PlaidConfig {

    private final Map<String, String> fileValues;

    private PlaidConfig(Map<String, String> fileValues) {
        this.fileValues = fileValues;
    }

    public static PlaidConfig load() {
        Path envPath = Path.of(System.getProperty("user.dir"), ".env");
        try {
            return new PlaidConfig(EnvFile.parse(envPath));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + envPath, e);
        }
    }

    public String clientId() {
        return require("PLAID_CLIENT_ID");
    }

    public String secret() {
        return require("PLAID_SECRET");
    }

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(get("PLAID_ENV", "production"));
    }

    public List<String> countryCodes() {
        String raw = get("PLAID_COUNTRY_CODES", "US");
        return raw.isBlank() ? List.of("US") : List.of(raw.split(","));
    }

    public String linkHost() {
        return get("LINK_HOST", "127.0.0.1");
    }

    public int linkPort() {
        return Integer.parseInt(get("LINK_PORT", "8765"));
    }

    private String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null) {
            return sys;
        }
        String env = System.getenv(key);
        if (env != null) {
            return env;
        }
        return fileValues.getOrDefault(key, defaultValue);
    }

    private String require(String key) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is not set (checked system property, env var, and .env)");
        }
        return value;
    }
}
