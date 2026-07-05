package com.sft.data;

import java.nio.file.Path;

/**
 * Resolves the path to the finance SQLite db (data/database.db, populated by
 * finance-ingest's Plaid sync). Override order: -Dsft.db.path system
 * property, then SFT_DB_PATH env var, then the local default.
 */
public final class DbConfig {

    public static Path resolveDbPath() {
        String override = System.getProperty("sft.db.path", System.getenv("SFT_DB_PATH"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.dir"), "data", "database.db").normalize();
    }

    private DbConfig() {}
}
