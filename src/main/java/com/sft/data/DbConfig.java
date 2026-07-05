package com.sft.data;

import java.nio.file.Path;

/**
 * Resolves the path to finance-ingest's SQLite db (finance.db). Override
 * order: -Dsft.db.path system property, then SFT_DB_PATH env var, then a
 * default assuming sft_v2 and finance-ingest are checked out as sibling
 * directories (true today on both Mac dev and the Pi).
 */
public final class DbConfig {

    public static Path resolveDbPath() {
        String override = System.getProperty("sft.db.path", System.getenv("SFT_DB_PATH"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.dir"), "..", "finance-ingest", "finance.db")
            .normalize();
    }

    private DbConfig() {}
}
