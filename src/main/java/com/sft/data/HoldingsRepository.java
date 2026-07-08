package com.sft.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.sqlite.SQLiteConfig;

/**
 * Reads current positions out of finance-ingest's finance.db. Deliberately
 * plain Java (no javafx.* imports) so it's unit-testable the same way
 * Carousel.java is kept separate from App.java's Node-building code.
 */
public final class HoldingsRepository {

    // Only rows from each account's OVERALL latest sync run (not each
    // security's own independent latest) count as "currently held". A
    // security missing from the most recent /investments/holdings/get
    // response means the position was closed - Plaid simply stops
    // returning it, no new row gets written, so its last row must NOT be
    // treated as still-current just because it's the newest row that
    // exists for that particular security.
    private static final String QUERY = """
        WITH account_latest AS (
            SELECT account_id, MAX(as_of) AS latest_run
            FROM holdings
            GROUP BY account_id
        )
        SELECT a.name AS account_name, i.institution_name,
               s.ticker_symbol, s.name AS security_name,
               h.quantity, h.cost_basis, h.institution_value AS current_value,
               h.institution_price AS current_price, h.iso_currency AS currency, h.as_of,
               s.type AS security_type,
               json_extract(s.raw_json, '$.sector') AS sector,
               json_extract(s.raw_json, '$.industry') AS industry
        FROM holdings h
        JOIN account_latest al ON al.account_id = h.account_id AND al.latest_run = h.as_of
        JOIN accounts a ON a.account_id = h.account_id
        JOIN items    i ON i.item_id    = a.item_id
        JOIN securities s ON s.security_id = h.security_id
        ORDER BY i.institution_name, a.name, s.ticker_symbol
        """;

    private final Path dbPath;

    public HoldingsRepository(Path dbPath) {
        this.dbPath = dbPath;
    }

    public List<Holding> currentHoldings() {
        if (!Files.exists(dbPath)) {
            throw new HoldingsUnavailableException("finance.db not found at " + dbPath);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (
            Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + dbPath,
                config.toProperties()
            );
            PreparedStatement statement = conn.prepareStatement(QUERY);
            ResultSet rs = statement.executeQuery()
        ) {
            List<Holding> holdings = new ArrayList<>();
            while (rs.next()) {
                holdings.add(map(rs));
            }
            return List.copyOf(holdings);
        } catch (SQLException e) {
            throw new HoldingsUnavailableException("holdings query failed: " + e.getMessage(), e);
        }
    }

    private static Holding map(ResultSet rs) throws SQLException {
        return new Holding(
            rs.getString("institution_name"),
            rs.getString("account_name"),
            rs.getString("ticker_symbol"),
            rs.getString("security_name"),
            rs.getDouble("quantity"),
            nullableDouble(rs, "cost_basis"),
            rs.getDouble("current_value"),
            nullableDouble(rs, "current_price"),
            rs.getString("currency"),
            rs.getString("as_of"),
            rs.getString("security_type"),
            rs.getString("sector"),
            rs.getString("industry")
        );
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    // Commented out per instruction: no automated test wired into the build
    // this round, but keeping the intended coverage documented and ready to
    // uncomment (into tests/com/sft/data/HoldingsRepositoryTest.java, JUnit 5,
    // with a @TempDir SQLite file db) whenever testing comes back on for this
    // class.
    //
    // - latestSnapshotWinsOverOlderRows(): insert two holdings rows for the
    //   same (account_id, security_id) with different as_of values; assert
    //   currentHoldings() returns only the row with the max as_of. Verifies
    //   the append-only-snapshot query picks "current," not "every sync ever."
    //
    // - closedPositionDropsOutOfCurrentHoldings(): insert a security with a
    //   holdings row at an OLD as_of, then a second, different security for
    //   the same account at a NEWER as_of (simulating: the old security was
    //   sold and Plaid stopped returning it, the new one is what the latest
    //   sync actually saw). Assert currentHoldings() returns ONLY the newer
    //   security, not both. Verifies the account_latest join correctly
    //   excludes positions absent from the most recent sync run, rather than
    //   incorrectly surfacing every security's own independent "latest" row.
    //
    // - nullTickerDoesNotBreakMapping(): insert a security row with a NULL
    //   ticker_symbol (mirrors a real cash position in finance.db). Assert
    //   currentHoldings() returns it with tickerSymbol() == null and
    //   hasResolvedTicker() == false, rather than throwing. Verifies cash/
    //   unresolved securities render instead of crashing the page.
    //
    // - missingDbFileThrows(): construct a HoldingsRepository pointed at a
    //   nonexistent path; assert currentHoldings() throws
    //   HoldingsUnavailableException. Verifies the UI's "db missing" error
    //   state is actually reachable rather than an uncaught SQLException.
    //
    // - emptySchemaReturnsEmptyList(): create the four tables with zero rows;
    //   assert currentHoldings() returns an empty (not null, not throwing)
    //   list. Verifies the "no holdings yet" UI state is distinct from the
    //   error state.
}
