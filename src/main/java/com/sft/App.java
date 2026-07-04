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
import javafx.scene.input.KeyEvent;
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
    private static final double VIEW_W = SCREEN_W; // pages are full-bleed; only the gear floats over them
    private static final double NAV_W = 60; // width of the drawer ✕ / Esc buttons
    private static final double SCROLL_STEP = 0.15; // how far arrow keys nudge the page (vvalue is 0..1)
    private static final Duration SLIDE = Duration.millis(250); // carousel/drawer animation time

    // "Calls" Pages; Pages are defined in Pages.java.
    private final List<Page> pages = Pages.ALL;

    private final HBox track = new HBox(); // [clone(last), real pages..., clone(first)]; the carousel "track"
    // Each track frame's ScrollPane, in the SAME order as track children (clones
    // included). pager.pos() indexes straight into this so the Up/Down keys
    // scroll whatever page is currently on screen.
    private final List<ScrollPane> scrollers = new ArrayList<>();
    private final int n = pages.size(); // number of real pages
    private final Carousel pager = new Carousel(n); // owns pos + the wrap math (Carousel.java)
    private boolean animating = false; // ignore taps mid-slide

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

        // Navigation is swipe (touch/Pi) + arrow keys (keyboard/computer) — see
        // the swipe handlers above and scene.setOnKeyPressed below. No on-screen
        // arrows. The only floating control is the settings gear.
        Button settings = new Button("⚙"); // ⚙
        settings.getStyleClass().add("icon-nav"); // .icon-nav sets NO font prop (see below)
        // The Pi's default font has no gear glyph (U+2699). icons.ttf (Noto Sans
        // Symbols, in resources) has it. Apply the loaded Font OBJECT directly via
        // setFont — not a CSS -fx-font-family name string (that lookup silently
        // failed and left the glyph blank on Mac and Pi). For setFont to stick,
        // .icon-nav must not set any -fx-font-* property, else CSS re-derives the
        // font and clobbers it.
        var iconStream = getClass().getResourceAsStream("/com/sft/icons.ttf");
        if (iconStream == null) {
            System.err.println("[icons] resource /com/sft/icons.ttf NOT on classpath — stale build? run `mvn clean compile javafx:run`");
        }
        Font icons = Font.loadFont(iconStream, 28);
        if (icons != null) {
            settings.setFont(icons);
        } else {
            System.err.println("[icons] Font.loadFont returned null — gear glyph will be blank");
        }

        // viewport is the full-bleed content; the gear floats over its top-right corner.
        StackPane carousel = new StackPane(viewport, settings);
        StackPane.setAlignment(settings, Pos.TOP_RIGHT);

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

        // Keyboard nav (computer): Left/Right move the carousel, Up/Down scroll the
        // current page. Touch swipe (Pi) is wired on the viewport above.
        //
        // This MUST be an event filter, not scene.setOnKeyPressed. A ScrollPane's
        // default skin handles arrow keys itself (to scroll) when it has focus, and
        // that on-target handling runs before a bubble-phase handler at the Scene
        // ever sees the event — so the keys silently never reached go()/nudge().
        // A filter runs during the capture phase (Scene -> ... -> target), before
        // the target's own key handling, so we get first crack and consume() it.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case LEFT -> go(-1);
                case RIGHT -> go(+1);
                case UP -> nudge(-SCROLL_STEP);
                case DOWN -> nudge(+SCROLL_STEP);
                default -> { return; }
            }
            e.consume();
        });

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
        sp.setPannable(true); // finger/drag to scroll vertically
        // No scrollbar chrome — scrolling is finger-drag (touch/Pi) or the Up/Down
        // arrow keys (nudge(), computer), both wired above. AS_NEEDED would still
        // draw a bar whenever content overflows; NEVER suppresses it on both bars
        // without disabling the scrolling itself.
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
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
