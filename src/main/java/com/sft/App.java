package com.sft;

import java.util.List;
import java.util.function.Supplier;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // Entire App

    // Pi screen size
    private static final double SCREEN_W = 800;
    private static final double SCREEN_H = 400;
    private static final double NAV_W = 60; // position width of arrow/settings buttons
    private static final double DIVIDER_W = 6; // divider width
    // Carousel width
    private static final double VIEW_W = SCREEN_W - 2 * NAV_W - 2 * DIVIDER_W; // wft is this
    private static final Duration SLIDE = Duration.millis(250); // carousel animation time

    // "Calls" Pages; Pages are defined in Pages.java.
    private final List<Supplier<Node>> pages = Pages.ALL;

    private final HBox track = new HBox(); // [clone(last), real pages..., clone(first)]; this is the carousel "track"/list?
    private final int n = pages.size(); // number of real pages
    private int pos = 1; // current track slot; real pages live at 1..n
    private boolean animating = false; // ignore taps mid-slide, mid slide animation?

    @Override
    // stage: holds title of app "shell"
    public void start(Stage stage) {
        // Build the extended track: a clone of the last page at the front and a
        // clone of the first page at the end. Sliding onto a clone then snapping
        // to the identical real page is what makes the wrap seamless in both
        // directions. (A node can't appear twice in the scene graph, so clones
        // are fresh nodes built by the Pages factories.)
        //
        // Above is cool. Curious if "clones" are kept and originals deleted, or clones deleted or neither
        // not certian on below line and relation through block. i think sets lenght of "frames" which hold pages
        track.getChildren().add(frame(pages.get(n - 1).get()));
        for (Supplier<Node> p : pages) {
            track.getChildren().add(frame(p.get())); // all pages/children
        }
        track.getChildren().add(frame(pages.get(0).get())); // clone of first
        track.setTranslateX(-pos * VIEW_W); // start on the first real page

        // Plain Pane positions the track at (0,0
        // Clip so off-screen pages stay hidden, and pin the Pane to the view size.
        // Panes/buttons config; intuitive
        Pane viewport = new Pane(track);
        viewport.setClip(new Rectangle(VIEW_W, SCREEN_H));
        viewport.setMinSize(VIEW_W, SCREEN_H);
        viewport.setPrefSize(VIEW_W, SCREEN_H);
        viewport.setMaxSize(VIEW_W, SCREEN_H);

        Button left = navButton("«"); // changed these up may again...i did i broke it originally :(
        Button right = navButton("»");
        Button settings = navButton("⚙"); // nice icon. good job claude
        left.setOnAction(e -> go(-1)); // enable navigation
        right.setOnAction(e -> go(+1));

        // Side buttons frame the viewport; light-blue dividers mark the seams.
        // Hbox used as omnipresent screen; below glues together? <- not 100% certian
        HBox row = new HBox(left, divider(), viewport, divider(), right);
        row.setAlignment(Pos.CENTER);

        // Settings floats over the top-right corner.
        StackPane carousel = new StackPane(row, settings);
        StackPane.setAlignment(settings, Pos.TOP_RIGHT); // alignment could get messy if adding to Hbox screen and not on Page

        // Settings drawer — parked off-screen till opened; config
        VBox drawer = settingsDrawer();
        drawer.setTranslateX(SCREEN_W);
        settings.setOnAction(e -> slide(drawer, 0)); // slide in
        ((Button) drawer.getChildren().get(0)).setOnAction(
            e -> slide(drawer, SCREEN_W) // close button slides it back out
        );

        StackPane root = new StackPane(carousel, drawer); // Stackpane root holds the carousel and the settings drawer, holds "entire screen"

        Scene scene = new Scene(root, SCREEN_W, SCREEN_H); // Scene is optimized 7" external display (PI DSI)
        scene
            .getStylesheets()
            .add(
                getClass().getResource("/com/sft/styles.css").toExternalForm()
            ); // ...pull stylesheet

        stage.setTitle("SFT");
        stage.setScene(scene); // App
        stage.setResizable(false); // match the fixed Pi screen during desktop testing; could optimize further later -> lots of work :(
        boolean onPi = System.getProperty("os.name")
            .toLowerCase()
            .contains("linux");
        if (onPi) {
            stage.setFullScreen(true); // full screen when launched on Pi. 6/23 need to add escape button
            stage.setFullScreenExitHint(""); // removed exit hint from view (stage)
        }
        stage.show();
    }

    // Advance the carousel by dir (+1 / -1), wrapping seamlessly at both ends.
    private void go(int dir) {
        if (animating) return; // dont move while animating
        animating = true;
        pos += dir;
        TranslateTransition t = new TranslateTransition(SLIDE, track); //TranslationTranslation object... fancy. moves throught track by t to slide
        // got bored reading around here.
        t.setToX(-pos * VIEW_W); // VIEW_W = full screen width
        t.setOnFinished(e -> {
            // We've slid onto a clone — snap (no animation) to the real twin.
            if (pos == 0) {
                // clone of last page → real last page
                pos = n;
                track.setTranslateX(-pos * VIEW_W);
            } else if (pos == n + 1) {
                // clone of first page → real first page
                pos = 1;
                track.setTranslateX(-pos * VIEW_W);
            }
            animating = false;
        });
        t.play(); // play and nice animation
    }

    // Animate a node's horizontal position.
    // what is the node?
    private void slide(Node node, double toX) {
        TranslateTransition t = new TranslateTransition(SLIDE, node);
        t.setToX(toX);
        t.play();
    }

    // Wrap a page's content (from Pages.java) in a full-size, centered, dark
    // frame. This is the unit the carousel slides; one frame == one screen.
    private VBox frame(Node content) {
        VBox box = new VBox(content);
        box.setAlignment(Pos.CENTER);
        box.setMinSize(VIEW_W, SCREEN_H);
        box.setPrefSize(VIEW_W, SCREEN_H);
        box.setStyle("-fx-background-color: #121212;"); // will likeley set per page. probably not appropiate to have here.
        return box;
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

    // A thin full-height line separating a side button from the picture.
    private Region divider() {
        // config for vertical dividers
        Region d = new Region();
        d.getStyleClass().add("divider");
        d.setMinSize(DIVIDER_W, SCREEN_H);
        d.setPrefSize(DIVIDER_W, SCREEN_H);
        d.setMaxSize(DIVIDER_W, SCREEN_H);
        return d; // positions of dividers based on width?
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
