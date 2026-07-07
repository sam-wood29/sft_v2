package com.sft.ingest.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One short-lived connection/transaction per logical unit of work - mirrors
 * the Python source's get_conn() context manager. Callers invoke this once
 * per transactions-sync page, once per whole balances/holdings/investment-tx/
 * liabilities call, etc., so a crash mid-run loses minimal progress.
 */
public final class IngestConnection {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    public static <T> T transact(Path dbPath, Work<T> work) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);
            try (Statement pragma = conn.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private IngestConnection() {}
}
