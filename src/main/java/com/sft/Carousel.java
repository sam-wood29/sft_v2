package com.sft;

/**
 * The carousel's wrap math, lifted out of App.java — no JavaFX in here, just the
 * slot bookkeeping, so it can be unit-tested without a display.
 *
 * Track layout: [clone(last)=0, page1=1, page2=2, ... pageN=n, clone(first)=n+1].
 * Real pages live in slots 1..n; slots 0 and n+1 are the invisible clones that
 * make the wrap seamless.
 */
final class Carousel {

    private final int n; // number of REAL pages
    private int pos = 1; // current slot; starts on the first real page

    Carousel(int n) {
        if (n < 1) throw new IllegalArgumentException(
            "carousel needs >= 1 page, got " + n
        );
        this.n = n;
    }

    int pos() {
        return pos;
    }

    /**
     * Phase 1 (while the slide animates): move the intent by dir (+1 / -1). May
     * land on a clone slot (0 or n+1) — that's intentional. Returns the slot to
     * animate to.
     */
    int target(int dir) {
        pos += dir;
        return pos;
    }

    /**
     * Phase 2 (when the slide finishes): if we landed on a clone, snap to its
     * real twin. Always leaves pos in 1..n. Returns it.
     */
    int settle() {
        if (pos == 0) pos = n;
        // clone of last page → real last page
        else if (pos == n + 1) pos = 1; // clone of first page → real first page
        return pos;
    }
}
