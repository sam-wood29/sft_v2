package com.sft.ingest.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public record Item(
    String itemId,
    String institutionName,
    String accessToken,
    Set<String> products,
    String cursor,
    String lastSyncedAt
) {
    public static Item fromRow(ResultSet rs) throws SQLException {
        return new Item(
            rs.getString("item_id"),
            rs.getString("institution_name"),
            rs.getString("access_token"),
            splitProducts(rs.getString("products")),
            rs.getString("cursor"),
            rs.getString("last_synced_at")
        );
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
}
