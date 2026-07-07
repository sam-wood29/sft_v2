package com.sft.ingest.check;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Shared staleness evaluation, reused by 3 of the 5 health-check sections.
 * Timestamps are the ISO-8601 UTC strings ("yyyy-MM-dd'T'HH:mm:ss'Z'")
 * written by the sync steps' shared SyncClock.
 */
public final class Staleness {

    public static CheckResult evaluate(
        String name,
        String timestampIso,
        double warnDays,
        double failDays,
        CheckStatus missingStatus
    ) {
        if (timestampIso == null || timestampIso.isBlank()) {
            return new CheckResult(name, missingStatus, "no snapshot");
        }
        double ageDays;
        try {
            Instant ts = Instant.parse(timestampIso);
            ageDays = (System.currentTimeMillis() / 1000.0 - ts.getEpochSecond()) / 86400.0;
        } catch (DateTimeParseException e) {
            return new CheckResult(name, missingStatus, "no snapshot");
        }
        if (ageDays > failDays) {
            return new CheckResult(name, CheckStatus.FAIL, String.format("%.1fd stale", ageDays));
        }
        if (ageDays > warnDays) {
            return new CheckResult(name, CheckStatus.WARN, String.format("%.1fd stale", ageDays));
        }
        return new CheckResult(name, CheckStatus.OK, String.format("%.1fd ago", ageDays));
    }

    private Staleness() {}
}
