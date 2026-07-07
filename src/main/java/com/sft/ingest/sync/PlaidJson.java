package com.sft.ingest.sync;

import com.google.gson.Gson;
import com.plaid.client.JSON;

/**
 * Raw-JSON serialization for Plaid response objects (the raw_json columns).
 * Reuses Plaid's own Gson instance, which has LocalDate/OffsetDateTime type
 * adapters already registered - a bare `new Gson()` would not serialize
 * those date fields correctly.
 */
public final class PlaidJson {

    private static final Gson GSON = new JSON().getGson();

    public static Gson gson() {
        return GSON;
    }

    public static String dump(Object o) {
        return GSON.toJson(o);
    }

    private PlaidJson() {}
}
