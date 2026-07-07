package com.sft.ingest.check;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The 5 health-check sections, ported directly from finance-ingest's check.py. */
public final class HealthChecks {

    private static final double STALE_WARN_DAYS = 2;
    private static final double STALE_FAIL_DAYS = 7;
    private static final double BALANCE_WARN_DAYS = 2;
    private static final double BALANCE_FAIL_DAYS = 7;

    public static List<CheckResult> itemsSynced(Connection conn) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        try (
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT institution_name, last_synced_at FROM items")
        ) {
            while (rs.next()) {
                results.add(
                    Staleness.evaluate(
                        "item:" + rs.getString("institution_name"),
                        rs.getString("last_synced_at"),
                        STALE_WARN_DAYS,
                        STALE_FAIL_DAYS,
                        CheckStatus.FAIL
                    )
                );
            }
        }
        return results;
    }

    public static List<CheckResult> cursors(Connection conn) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        try (
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT institution_name, products, cursor FROM items")
        ) {
            while (rs.next()) {
                Set<String> products = splitProducts(rs.getString("products"));
                if (!products.contains("transactions")) {
                    continue;
                }
                String cursor = rs.getString("cursor");
                boolean present = cursor != null && !cursor.isBlank();
                results.add(
                    new CheckResult(
                        "cursor:" + rs.getString("institution_name"),
                        present ? CheckStatus.OK : CheckStatus.FAIL,
                        present ? "present" : "missing"
                    )
                );
            }
        }
        return results;
    }

    public static List<CheckResult> accountCoverage(Connection conn) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        String sql = """
            SELECT a.name, a.type,
                   (SELECT MAX(as_of) FROM account_balances b WHERE b.account_id = a.account_id) AS bal_as_of,
                   (SELECT MAX(as_of) FROM holdings h WHERE h.account_id = a.account_id) AS hold_as_of
            FROM accounts a
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String type = rs.getString("type");
                String name = rs.getString("name");
                boolean isInvestment = type != null && type.equalsIgnoreCase("investment");
                String label = isInvestment ? "holdings" : "balance";
                String asOf = isInvestment ? rs.getString("hold_as_of") : rs.getString("bal_as_of");
                results.add(
                    Staleness.evaluate(label + ":" + name, asOf, BALANCE_WARN_DAYS, BALANCE_FAIL_DAYS, CheckStatus.FAIL)
                );
            }
        }
        return results;
    }

    public static List<CheckResult> liabilitySnapshots(Connection conn) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        String sql = """
            SELECT a.name,
                   (SELECT MAX(l.as_of) FROM liabilities_credit l WHERE l.account_id = a.account_id) AS as_of
            FROM accounts a
            JOIN items i ON i.item_id = a.item_id
            WHERE a.type = 'credit'
              AND ',' || COALESCE(i.products, '') || ',' LIKE '%,liabilities,%'
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                results.add(
                    Staleness.evaluate(
                        "liability:" + rs.getString("name"),
                        rs.getString("as_of"),
                        BALANCE_WARN_DAYS,
                        BALANCE_FAIL_DAYS,
                        CheckStatus.WARN // deliberate exception: missing liability snapshot is a warning, not a fail
                    )
                );
            }
        }
        return results;
    }

    public static List<CheckResult> schemaSanity(Connection conn) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        results.add(
            countCheck(
                conn,
                "transactions.amount not null",
                "SELECT COUNT(*) FROM transactions WHERE amount IS NULL",
                "null"
            )
        );
        results.add(
            countCheck(
                conn,
                "holdings → securities FK",
                "SELECT COUNT(*) FROM holdings h LEFT JOIN securities s ON s.security_id = h.security_id WHERE s.security_id IS NULL",
                "orphans"
            )
        );
        results.add(
            countCheck(
                conn,
                "investment_transactions → securities FK",
                """
                SELECT COUNT(*) FROM investment_transactions it
                LEFT JOIN securities s ON s.security_id = it.security_id
                WHERE it.security_id IS NOT NULL AND s.security_id IS NULL
                """,
                "orphans"
            )
        );
        return results;
    }

    private static CheckResult countCheck(Connection conn, String name, String sql, String unit) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            int count = rs.getInt(1);
            CheckStatus status = count > 0 ? CheckStatus.FAIL : CheckStatus.OK;
            return new CheckResult(name, status, count + " " + unit);
        }
    }

    private static Set<String> splitProducts(String raw) {
        Set<String> products = new LinkedHashSet<>();
        if (raw == null) {
            return products;
        }
        for (String p : raw.split(",")) {
            String trimmed = p.strip();
            if (!trimmed.isEmpty()) {
                products.add(trimmed);
            }
        }
        return products;
    }

    private HealthChecks() {}
}
