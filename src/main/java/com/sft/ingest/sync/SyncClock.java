package com.sft.ingest.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** One shared clock per sync run - matches Python's single as_of/today computed once in sync_all(). */
public record SyncClock(String asOfIso, LocalDate today) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC);

    public static SyncClock now() {
        Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new SyncClock(FORMAT.format(instant), LocalDate.now(ZoneOffset.UTC));
    }
}
