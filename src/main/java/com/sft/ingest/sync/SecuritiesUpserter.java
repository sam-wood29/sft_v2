package com.sft.ingest.sync;

import com.plaid.client.model.Security;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Shared securities upsert - the Python source duplicates this identically
 * in _sync_holdings and _sync_investment_transactions (the latter because
 * sold/historical securities can appear there but not in current holdings).
 */
public final class SecuritiesUpserter {

    private static final String UPSERT = """
        INSERT OR REPLACE INTO securities
          (security_id, ticker_symbol, name, type, iso_currency, raw_json)
        VALUES (?,?,?,?,?,?)
        """;

    public static void upsert(Connection conn, List<Security> securities) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            for (Security sec : securities) {
                ps.setString(1, sec.getSecurityId());
                ps.setString(2, sec.getTickerSymbol());
                ps.setString(3, sec.getName());
                ps.setString(4, sec.getType() != null ? sec.getType().toString() : null);
                ps.setString(5, sec.getIsoCurrencyCode());
                ps.setString(6, PlaidJson.dump(sec));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private SecuritiesUpserter() {}
}
