package com.sft.ingest.sync;

import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.db.IngestConnection;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

/** Cursor-based /transactions/sync loop - never /transactions/get, per the source project's own rule. */
public final class TransactionsSyncStep {

    public record Counts(int added, int modified, int removed) {}

    private static final String UPSERT = """
        INSERT OR REPLACE INTO transactions
          (transaction_id, account_id, date, authorized_date, name, merchant_name,
           amount, iso_currency, plaid_category, plaid_personal_finance_category,
           pending, raw_json)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private static final String DELETE = "DELETE FROM transactions WHERE transaction_id = ?";
    private static final String UPDATE_CURSOR = "UPDATE items SET cursor = ? WHERE item_id = ?";

    public Counts sync(PlaidApi client, Path dbPath, String itemId, String accessToken, String cursor)
        throws IOException, SQLException {
        int added = 0;
        int modified = 0;
        int removed = 0;
        String nextCursor = cursor == null ? "" : cursor;
        while (true) {
            TransactionsSyncRequest request = new TransactionsSyncRequest()
                .accessToken(accessToken)
                .cursor(nextCursor);
            Response<TransactionsSyncResponse> response = client.transactionsSync(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("transactionsSync failed: HTTP " + response.code());
            }
            TransactionsSyncResponse body = response.body();
            List<Transaction> upserts = new ArrayList<>(body.getAdded());
            upserts.addAll(body.getModified());
            List<RemovedTransaction> removedList = body.getRemoved();
            String finalNextCursor = body.getNextCursor();

            IngestConnection.transact(dbPath, conn -> {
                upsertAll(conn, upserts);
                deleteAll(conn, removedList);
                updateCursor(conn, itemId, finalNextCursor);
                return null;
            });

            added += body.getAdded().size();
            modified += body.getModified().size();
            removed += removedList.size();
            nextCursor = finalNextCursor;
            if (!Boolean.TRUE.equals(body.getHasMore())) {
                break;
            }
        }
        return new Counts(added, modified, removed);
    }

    private void upsertAll(Connection conn, List<Transaction> txs) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            for (Transaction tx : txs) {
                ps.setString(1, tx.getTransactionId());
                ps.setString(2, tx.getAccountId());
                ps.setString(3, tx.getDate() != null ? tx.getDate().toString() : null);
                ps.setString(4, tx.getAuthorizedDate() != null ? tx.getAuthorizedDate().toString() : null);
                ps.setString(5, tx.getName());
                ps.setString(6, tx.getMerchantName());
                setNullableDouble(ps, 7, tx.getAmount());
                ps.setString(8, tx.getIsoCurrencyCode());
                ps.setString(9, tx.getCategory() != null ? String.join(",", tx.getCategory()) : null);
                PersonalFinanceCategory pfc = tx.getPersonalFinanceCategory();
                ps.setString(10, pfc != null ? pfc.getPrimary() : null);
                ps.setInt(11, Boolean.TRUE.equals(tx.getPending()) ? 1 : 0);
                ps.setString(12, PlaidJson.dump(tx));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteAll(Connection conn, List<RemovedTransaction> removedList) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DELETE)) {
            for (RemovedTransaction r : removedList) {
                ps.setString(1, r.getTransactionId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void updateCursor(Connection conn, String itemId, String cursor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_CURSOR)) {
            ps.setString(1, cursor);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
