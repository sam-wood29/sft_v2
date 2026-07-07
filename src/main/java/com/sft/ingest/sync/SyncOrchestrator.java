package com.sft.ingest.sync;

import com.plaid.client.request.PlaidApi;
import com.sft.ingest.PlaidClientFactory;
import com.sft.ingest.config.PlaidConfig;
import com.sft.ingest.db.IngestConnection;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * sync_all() equivalent: fetch all items unconditionally, run each gated sync
 * step with per-endpoint error isolation (not per-item) so one failing
 * endpoint doesn't block the rest of that item or the next item.
 */
public final class SyncOrchestrator {

    public static void syncAll(Path dbPath, PlaidConfig config) throws SQLException {
        PlaidApi client = PlaidClientFactory.create(config);
        List<Item> items = fetchItems(dbPath);
        SyncClock clock = SyncClock.now();

        TransactionsSyncStep transactionsStep = new TransactionsSyncStep();
        BalancesSyncStep balancesStep = new BalancesSyncStep();
        HoldingsSyncStep holdingsStep = new HoldingsSyncStep();
        InvestmentTransactionsSyncStep investmentTxStep = new InvestmentTransactionsSyncStep();
        LiabilitiesSyncStep liabilitiesStep = new LiabilitiesSyncStep();

        for (Item item : items) {
            System.out.println("\n[" + item.institutionName() + "] products=" + item.products());

            if (item.products().contains("transactions")) {
                try {
                    var counts = transactionsStep.sync(client, dbPath, item.itemId(), item.accessToken(), item.cursor());
                    System.out.println(
                        "  transactions: +" + counts.added() + " ~" + counts.modified() + " -" + counts.removed()
                    );
                } catch (Exception e) {
                    System.out.println("  transactions ERROR: " + e.getMessage());
                }
            }

            try {
                int n = balancesStep.sync(client, dbPath, item.accessToken(), clock.asOfIso());
                System.out.println("  balances: " + n + " accounts");
            } catch (Exception e) {
                System.out.println("  balances ERROR: " + e.getMessage());
            }

            if (item.products().contains("investments")) {
                try {
                    var counts = holdingsStep.sync(client, dbPath, item.accessToken(), clock.asOfIso());
                    System.out.println(
                        "  holdings: " + counts.holdings() + " positions, " + counts.securities() + " securities"
                    );
                } catch (Exception e) {
                    System.out.println("  holdings ERROR: " + e.getMessage());
                }

                LocalDate start = item.lastSyncedAt() != null
                    ? parseDate(item.lastSyncedAt()).minusDays(7)
                    : clock.today().minusDays(365L * 5);
                try {
                    int n = investmentTxStep.sync(client, dbPath, item.accessToken(), start, clock.today());
                    System.out.println("  investment_transactions: " + n + " (" + start + ".." + clock.today() + ")");
                } catch (Exception e) {
                    System.out.println("  investment_transactions ERROR: " + e.getMessage());
                }
            }

            if (item.products().contains("liabilities")) {
                try {
                    int n = liabilitiesStep.sync(client, dbPath, item.accessToken(), clock.asOfIso());
                    System.out.println("  liabilities: " + n + " credit accounts");
                } catch (Exception e) {
                    System.out.println("  liabilities ERROR: " + e.getMessage());
                }
            }

            updateLastSynced(dbPath, item.itemId(), clock.asOfIso());
        }
    }

    private static LocalDate parseDate(String isoInstant) {
        return Instant.parse(isoInstant).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static List<Item> fetchItems(Path dbPath) throws SQLException {
        return IngestConnection.transact(dbPath, conn -> {
            List<Item> items = new ArrayList<>();
            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                    "SELECT item_id, institution_name, access_token, products, cursor, last_synced_at FROM items"
                )
            ) {
                while (rs.next()) {
                    items.add(Item.fromRow(rs));
                }
            }
            return items;
        });
    }

    private static void updateLastSynced(Path dbPath, String itemId, String asOf) throws SQLException {
        IngestConnection.transact(dbPath, conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE items SET last_synced_at = ? WHERE item_id = ?")) {
                ps.setString(1, asOf);
                ps.setString(2, itemId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private SyncOrchestrator() {}
}
