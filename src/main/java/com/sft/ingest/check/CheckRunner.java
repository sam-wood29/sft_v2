package com.sft.ingest.check;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Orchestrates the 5 health-check sections, prints plain-text tables, returns an exit code. */
public final class CheckRunner {

    private static final List<String> TABLES = List.of(
        "items",
        "accounts",
        "account_balances",
        "transactions",
        "securities",
        "holdings",
        "investment_transactions",
        "liabilities_credit"
    );

    public static int run(Path dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            printOverview(conn, dbPath);

            Map<String, List<CheckResult>> sections = new LinkedHashMap<>();
            sections.put("items synced", HealthChecks.itemsSynced(conn));
            sections.put("cursors", HealthChecks.cursors(conn));
            sections.put("account snapshots", HealthChecks.accountCoverage(conn));
            sections.put("liability snapshots", HealthChecks.liabilitySnapshots(conn));
            sections.put("schema sanity", HealthChecks.schemaSanity(conn));

            Set<CheckStatus> statuses = java.util.EnumSet.noneOf(CheckStatus.class);
            for (var entry : sections.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                printResults(entry.getKey(), entry.getValue());
                for (CheckResult r : entry.getValue()) {
                    statuses.add(r.status());
                }
            }

            if (statuses.contains(CheckStatus.FAIL)) {
                System.out.println("✗ FAIL");
                return 2;
            }
            if (statuses.contains(CheckStatus.WARN)) {
                System.out.println("⚠ WARN");
                return 1;
            }
            System.out.println("✓ OK");
            return 0;
        }
    }

    private static void printOverview(Connection conn, Path dbPath) throws SQLException {
        System.out.println("database.db @ " + dbPath);
        System.out.printf("%-28s %6s%n", "table", "rows");
        try (Statement st = conn.createStatement()) {
            for (String table : TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    System.out.printf("%-28s %6d%n", table, rs.getInt(1));
                }
            }
        }
    }

    private static void printResults(String title, List<CheckResult> results) {
        System.out.println("\n" + title);
        for (CheckResult r : results) {
            String icon =
                switch (r.status()) {
                    case OK -> "✓";
                    case WARN -> "⚠";
                    case FAIL -> "✗";
                };
            System.out.printf("%s %-40s %s%n", icon, r.name(), r.summary());
        }
    }

    private CheckRunner() {}
}
