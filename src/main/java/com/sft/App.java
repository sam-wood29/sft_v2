package com.sft;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // Entire App

    private static final double SCREEN_W = 800; // Pi screen size
    private static final double SCREEN_H = 400; // Pi screen size
    private static final double VIEW_W = SCREEN_W; // pages are now full-bleed; nav floats over them
    private static final double NAV_W = 60; // width of the nav-panel buttons
    private static final double PANEL_W = 140; // collapsible right nav panel width
    private static final double HANDLE_W = 24; // always-visible edge handle that toggles the panel
    private static final double SCROLL_STEP = 0.15; // how far ˄/˅ nudge the page (vvalue is 0..1)
    private static final Duration SLIDE = Duration.millis(250); // carousel/panel animation time

    // "Calls" Pages; Pages are defined in Pages.java.
    private final List<Page> pages = Pages.ALL;

    private final HBox track = new HBox(); // [clone(last), real pages..., clone(first)]; the carousel "track"
    // Each track frame's ScrollPane, in the SAME order as track children (clones
    // included). pager.pos() indexes straight into this so ˄/˅ scroll whatever
    // page is currently on screen.
    private final List<ScrollPane> scrollers = new ArrayList<>();
    private final int n = pages.size(); // number of real pages
    private final Carousel pager = new Carousel(n); // owns pos + the wrap math (Carousel.java)
    private boolean animating = false; // ignore taps mid-slide
    private boolean panelOpen = false; // is the right nav panel slid in?

    @Override
    // stage: holds title of app "shell"
    public void start(Stage stage) {
        // Build the extended track: a clone of the last page at the front and a
        // clone of the first page at the end. Sliding onto a clone then snapping
        // to the identical real page is what makes the wrap seamless in both
        // directions. (A node can't appear twice in the scene graph, so clones
        // are fresh nodes built by the Page factories.)
        track.getChildren().add(frame(pages.get(n - 1).content()));
        for (Page p : pages) {
            track.getChildren().add(frame(p.content())); // all real pages
        }
        track.getChildren().add(frame(pages.get(0).content())); // clone of first
        track.setTranslateX(-pager.pos() * VIEW_W); // start on the first real page

        // Plain Pane positions the track at (0,0). Clip so off-screen pages stay
        // hidden, and pin the Pane to the full screen — content is full-bleed now.
        Pane viewport = new Pane(track);
        viewport.setClip(new Rectangle(VIEW_W, SCREEN_H));
        viewport.setMinSize(VIEW_W, SCREEN_H);
        viewport.setPrefSize(VIEW_W, SCREEN_H);
        viewport.setMaxSize(VIEW_W, SCREEN_H);
        // Horizontal swipe drives the carousel; vertical drag scrolls the page
        // (that's the ScrollPane's own pannable behavior, set in frame()).
        viewport.setOnSwipeLeft(e -> go(+1));
        viewport.setOnSwipeRight(e -> go(-1));

        // Nav lives in a collapsible right panel now, not framing the viewport.
        Button prev = navButton("‹"); // carousel prev
        Button next = navButton("›"); // carousel next
        Button up = navButton("˄"); // scroll current page up
        Button down = navButton("˅"); // scroll current page down
        Button settings = navButton("⚙");
        // The Pi's default font has no gear glyph (U+2699), so ⚙ shows on Mac but
        // is blank on the Pi. Bundle a font that has it (icons.ttf = Noto Sans
        // Symbols, in resources) and use it just for this button — works anywhere.
        // loadFont registers the family; apply via inline -fx-font-family because the
        // .nav rule's -fx-font-size would otherwise override a plain setFont() call.
        var iconStream = getClass().getResourceAsStream("/com/sft/icons.ttf");
        if (iconStream == null) {
            System.err.println("[icons] resource /com/sft/icons.ttf NOT on classpath — stale build? run `mvn clean compile javafx:run`");
        }
        Font icons = Font.loadFont(iconStream, 28);
        if (icons != null) {
            // inline -fx-font-size too, so the .nav rule's size doesn't override the family swap
            settings.setStyle(
                "-fx-font-family: '" + icons.getFamily() + "'; -fx-font-size: 28px;"
            );
        } else {
            System.err.println("[icons] Font.loadFont returned null — gear glyph will be blank on the Pi");
        }
        prev.setOnAction(e -> go(-1));
        next.setOnAction(e -> go(+1));
        up.setOnAction(e -> nudge(-SCROLL_STEP)); // ˄ = toward the top = lower vvalue
        down.setOnAction(e -> nudge(+SCROLL_STEP));

        // The right nav panel: page arrows, scroll arrows, settings. Parked just
        // off the right edge; slides in via the handle. Same trick the settings
        // drawer already uses.
        HBox pageRow = new HBox(prev, next);
        pageRow.setAlignment(Pos.CENTER);
        VBox panel = new VBox(settings, up, pageRow, down);
        panel.getStyleClass().add("nav-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setMinWidth(PANEL_W);
        panel.setPrefWidth(PANEL_W);
        panel.setMaxWidth(PANEL_W);
        panel.setPrefHeight(SCREEN_H);
        panel.setTranslateX(PANEL_W); // parked off-screen to the right

        // Thin always-visible handle at the right edge: tap to slide the panel in/out.
        Button handle = new Button("‹");
        handle.getStyleClass().add("handle");
        handle.setMinWidth(HANDLE_W);
        handle.setPrefWidth(HANDLE_W);
        handle.setMaxWidth(HANDLE_W);
        handle.setPrefHeight(SCREEN_H);
        handle.setOnAction(e -> {
            panelOpen = !panelOpen;
            slide(panel, panelOpen ? 0 : PANEL_W);
            handle.setText(panelOpen ? "›" : "‹");
        });

        // viewport is the full-bleed content; panel + handle float over its right edge.
        StackPane carousel = new StackPane(viewport, panel, handle);
        StackPane.setAlignment(viewport, Pos.CENTER);
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);
        StackPane.setAlignment(handle, Pos.CENTER_RIGHT);

        // Settings drawer — parked off-screen till opened; config
        VBox drawer = settingsDrawer();
        drawer.setTranslateX(SCREEN_W);
        settings.setOnAction(e -> slide(drawer, 0)); // slide in
        ((Button) drawer.getChildren().get(0)).setOnAction(
            e -> slide(drawer, SCREEN_W) // close button slides it back out
        ); // potential ClassCastExeption() - close button grabbed by getChildren().get(0) cast, reordering drawers children silently

        StackPane root = new StackPane(carousel, drawer); // Stackpane root holds the carousel and the settings drawer, holds "entire screen"

        Scene scene = new Scene(root, SCREEN_W, SCREEN_H); // Scene is optimized 7" external display (PI DSI)
        scene
            .getStylesheets()
            .add(
                getClass().getResource("/com/sft/styles.css").toExternalForm()
            ); // ...pull stylesheet; potential null pointer exception if no stylesheet is found.

        stage.setTitle("SFT");
        stage.setScene(scene); // App
        stage.setResizable(false); // match the fixed Pi screen during desktop testing; could optimize further later -> lots of work :(
        boolean onPi = System.getProperty("os.name")
            .toLowerCase()
            .contains("linux");
        if (onPi) {
            stage.setFullScreen(true); // full screen when launched on Pi
            stage.setFullScreenExitHint(""); // removed exit hint from view (stage)

            // Top-left escape out of full screen. Plain "Esc" text (no glyph) so it
            // can't hit the same missing-font problem we're trying to debug.
            Button escape = navButton("Esc");
            escape.setOnAction(e -> stage.setFullScreen(false));
            root.getChildren().add(escape); // top-most over carousel + drawer
            StackPane.setAlignment(escape, Pos.TOP_LEFT);
        }
        stage.show();
    }

    // Advance the carousel by dir (+1 / -1), wrapping seamlessly at both ends.
    private void go(int dir) {
        if (animating) return; // dont move while animating
        animating = true;
        int slot = pager.target(dir); // phase 1: slide onto this slot (may be a clone)
        TranslateTransition t = new TranslateTransition(SLIDE, track); //TranslationTranslation object... fancy. moves throught track by t to slide
        t.setToX(-slot * VIEW_W); // VIEW_W = full screen width
        t.setOnFinished(e -> {
            // We've slid onto a clone — snap (no animation) to the real twin. (logic in Carousel.settle)
            int settled = pager.settle();
            track.setTranslateX(-settled * VIEW_W);
            animating = false;
        });
        t.play(); // play and nice animation
    }

    // Nudge the currently-visible page's vertical scroll by delta (vvalue is 0..1).
    private void nudge(double delta) {
        ScrollPane sp = scrollers.get(pager.pos());
        sp.setVvalue(Math.max(0, Math.min(1, sp.getVvalue() + delta)));
    }

    // Animate a node's horizontal position (settings drawer + nav panel).
    private void slide(Node node, double toX) {
        TranslateTransition t = new TranslateTransition(SLIDE, node);
        t.setToX(toX);
        t.play();
    }

    // Wrap a page's content (from Pages.java) in a full-size, dark, vertically
    // scrollable frame. This is the unit the carousel slides; one frame == one
    // screen. Short content centers; content taller than the screen scrolls.
    private ScrollPane frame(Node content) {
        VBox inner = new VBox(content);
        inner.setAlignment(Pos.CENTER);
        inner.setFillWidth(true);
        inner.setMinHeight(SCREEN_H); // fill the screen so short pages stay centered
        inner.setStyle("-fx-background-color: #121212;"); // will likeley set per page later

        ScrollPane sp = new ScrollPane(inner);
        sp.getStyleClass().add("page-scroll");
        sp.setFitToWidth(true);
        sp.setPannable(true); // touch/drag to scroll vertically
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setMinSize(VIEW_W, SCREEN_H);
        sp.setPrefSize(VIEW_W, SCREEN_H);
        sp.setMaxSize(VIEW_W, SCREEN_H);
        scrollers.add(sp);
        return sp;
    }

    private VBox settingsDrawer() {
        // config for open settings menu
        Button close = navButton("✕"); // index 0 — wired up in start()
        VBox box = new VBox(close, new Label("Settings"));
        box.getStyleClass().add("drawer");
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefSize(SCREEN_W, SCREEN_H);
        return box;
    }

    private Button navButton(String glyph) {
        // nav button config, settings button too?
        Button b = new Button(glyph);
        b.getStyleClass().add("nav");
        b.setMinWidth(NAV_W);
        b.setPrefWidth(NAV_W);
        b.setMaxWidth(NAV_W);
        return b;
    }

    public static void main(String[] args) {
        launch();
    }
}
