package com.sft;

import java.util.List;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The carousel's pages live here — one method per page. Edit the bodies to
 * change what each page shows; this file has nothing to do with the carousel's
 * sliding/wrapping (that's all in App.java).
 *
 * Each page is a Supplier<Node>: a factory that builds a FRESH node every time
 * it's called. That matters because the carousel builds invisible clones of the
 * first and last pages for its seamless wrap, and a JavaFX node can't appear in
 * the scene twice. As long as each method returns a new node, cloning is free.
 *
 * App.java wraps whatever you return here in a full-size, centered, dark frame,
 * so you only need to return the content (a label, an HBox of widgets, etc.).
 */
public final class Pages {

    // The page order. Add, remove, or reorder entries here.
    public static final List<Supplier<Node>> ALL = List.of(
        Pages::page1,
        Pages::page2,
        Pages::page3,
        Pages::page4
    );

    private static Node page1() {
        return bigText("PAGE 1");
    }

    private static Node page2() {
        return bigText("PAGE 2");
    }

    private static Node page3() {
        return bigText("PAGE 3");
    }

    private static Node page4() {
        return bigText("PAGE 4");
    }

    // Shared helper: one big centered label. Replace per page with real content.
    private static Node bigText(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 64px; -fx-font-weight: bold;");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Pages() {} // static-only; never instantiated
}
