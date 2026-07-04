package com.sft;

import javafx.scene.Node;

/**
 * One carousel page. Returns a FRESH content node each call — the carousel
 * clones the first/last page and a JavaFX node can't be in the scene twice.
 *
 * Free vertical scroll is applied by App.frame(); a future vertical-snap page
 * implements this same interface and owns its own up/down behavior, so the
 * carousel/App code never has to branch on what kind of page it is.
 */
@FunctionalInterface
public interface Page {
    Node content();
}
