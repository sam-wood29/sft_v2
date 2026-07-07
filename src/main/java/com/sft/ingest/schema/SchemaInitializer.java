package com.sft.ingest.schema;

import com.sft.ingest.db.IngestConnection;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The 8-table schema, ported verbatim from finance-ingest's db.py. Every
 * statement is idempotent (IF NOT EXISTS) - safe to run against the already-
 * populated database.
 */
public final class SchemaInitializer {

    private static final List<String> STATEMENTS = List.of(
        """
        CREATE TABLE IF NOT EXISTS items (
            item_id          TEXT PRIMARY KEY,
            institution_id   TEXT,
            institution_name TEXT NOT NULL,
            access_token     TEXT NOT NULL,
            products         TEXT,
            cursor           TEXT,
            last_synced_at   TEXT,
            created_at       TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS accounts (
            account_id     TEXT PRIMARY KEY,
            item_id        TEXT NOT NULL REFERENCES items(item_id) ON DELETE CASCADE,
            name           TEXT,
            official_name  TEXT,
            type           TEXT,
            subtype        TEXT,
            mask           TEXT,
            created_at     TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS account_balances (
            account_id   TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
            as_of        TEXT NOT NULL,
            current      REAL,
            available    REAL,
            iso_currency TEXT,
            PRIMARY KEY (account_id, as_of)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS transactions (
            transaction_id                  TEXT PRIMARY KEY,
            account_id                      TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
            date                            TEXT NOT NULL,
            authorized_date                 TEXT,
            name                            TEXT,
            merchant_name                   TEXT,
            amount                          REAL NOT NULL,
            iso_currency                    TEXT,
            plaid_category                  TEXT,
            plaid_personal_finance_category TEXT,
            pending                         INTEGER NOT NULL DEFAULT 0,
            raw_json                        TEXT NOT NULL,
            ingested_at                     TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_id)",
        """
        CREATE TABLE IF NOT EXISTS securities (
            security_id   TEXT PRIMARY KEY,
            ticker_symbol TEXT,
            name          TEXT,
            type          TEXT,
            iso_currency  TEXT,
            raw_json      TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS holdings (
            account_id        TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
            security_id       TEXT NOT NULL REFERENCES securities(security_id),
            as_of             TEXT NOT NULL,
            quantity          REAL,
            cost_basis        REAL,
            institution_value REAL,
            institution_price REAL,
            iso_currency      TEXT,
            PRIMARY KEY (account_id, security_id, as_of)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS investment_transactions (
            investment_transaction_id TEXT PRIMARY KEY,
            account_id                TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
            security_id               TEXT REFERENCES securities(security_id),
            date                      TEXT NOT NULL,
            type                      TEXT,
            subtype                   TEXT,
            amount                    REAL,
            quantity                  REAL,
            price                     REAL,
            fees                      REAL,
            iso_currency              TEXT,
            raw_json                  TEXT NOT NULL,
            ingested_at               TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_inv_tx_date ON investment_transactions(date)",
        """
        CREATE TABLE IF NOT EXISTS liabilities_credit (
            account_id                TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
            as_of                     TEXT NOT NULL,
            last_payment_amount       REAL,
            last_payment_date         TEXT,
            last_statement_balance    REAL,
            last_statement_issue_date TEXT,
            minimum_payment_amount    REAL,
            next_payment_due_date     TEXT,
            aprs_json                 TEXT,
            raw_json                  TEXT NOT NULL,
            PRIMARY KEY (account_id, as_of)
        )
        """
    );

    public static void initSchema(Path dbPath) throws SQLException {
        IngestConnection.transact(dbPath, conn -> {
            try (Statement st = conn.createStatement()) {
                for (String ddl : STATEMENTS) {
                    st.executeUpdate(ddl);
                }
                Set<String> cols = new HashSet<>();
                try (ResultSet rs = st.executeQuery("PRAGMA table_info(items)")) {
                    while (rs.next()) {
                        cols.add(rs.getString("name"));
                    }
                }
                if (!cols.contains("products")) {
                    st.executeUpdate("ALTER TABLE items ADD COLUMN products TEXT");
                }
            }
            return null;
        });
    }

    private SchemaInitializer() {}
}
