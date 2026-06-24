package com.sft;

import javafx.application.Application;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
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

    // Pi screen size — also the width of one carousel page.
    private static final double SCREEN_W = 800;
    private static final double SCREEN_H = 400;
    private static final double NAV_W = 60;       // arrow/settings button width (was 48, +25%)
    private static final double DIVIDER_W = 6;    // light-blue line between button and picture
    // Carousel is bounded between the side buttons, minus the two dividers.
    private static final double VIEW_W = SCREEN_W - 2 * NAV_W - 2 * DIVIDER_W;
    private static final Duration SLIDE = Duration.millis(250);

    // The carousel pages — title + background color. Add your own here.
    private static final String[][] PAGES = {
        { "PAGE 1", "#2e3a59" },
        { "PAGE 2", "#593a2e" },
        { "PAGE 3", "#2e593a" },
        { "PAGE 4", "#592e4f" },
    };

    private final HBox track = new HBox(); // [clone(last), real pages..., clone(first)]
    private final int n = PAGES.length;    // number of real pages
    private int pos = 1;                    // current track slot; real pages live at 1..n
    private boolean animating = false;      // ignore taps mid-slide

    @Override
    public void start(Stage stage) {
        // Build the extended track: a clone of the last page at the front and a
        // clone of the first page at the end. Sliding onto a clone then snapping
        // to the identical real page is what makes the wrap seamless in both
        // directions. (A node can't appear twice in the scene graph, so clones
        // are fresh VBoxes built from the same definition.)
        track.getChildren().add(page(PAGES[n - 1][0], PAGES[n - 1][1]));
        for (String[] p : PAGES) {
            track.getChildren().add(page(p[0], p[1]));
        }
        track.getChildren().add(page(PAGES[0][0], PAGES[0][1]));
        track.setTranslateX(-pos * VIEW_W); // start on the first real page

        // Plain Pane positions the track at (0,0) — a StackPane would center it.
        // Clip so off-screen pages stay hidden, and pin the Pane to the view size.
        Pane viewport = new Pane(track);
        viewport.setClip(new Rectangle(VIEW_W, SCREEN_H));
        viewport.setMinSize(VIEW_W, SCREEN_H);
        viewport.setPrefSize(VIEW_W, SCREEN_H);
        viewport.setMaxSize(VIEW_W, SCREEN_H);

        Button left = navButton("‹");
        Button right = navButton("›");
        Button settings = navButton("⚙");
        left.setOnAction(e -> go(-1));
        right.setOnAction(e -> go(+1));

        // Side buttons frame the viewport; light-blue dividers mark the seams.
        HBox row = new HBox(left, divider(), viewport, divider(), right);
        row.setAlignment(Pos.CENTER);

        // Settings floats over the top-right corner.
        StackPane carousel = new StackPane(row, settings);
        StackPane.setAlignment(settings, Pos.TOP_RIGHT);

        // Settings drawer — parked off-screen to the right until opened.
        VBox drawer = settingsDrawer();
        drawer.setTranslateX(SCREEN_W);
        settings.setOnAction(e -> slide(drawer, 0)); // slide in
        ((Button) drawer.getChildren().get(0)).setOnAction(
            e -> slide(drawer, SCREEN_W) // close button slides it back out
        );

        StackPane root = new StackPane(carousel, drawer);

        Scene scene = new Scene(root, SCREEN_W, SCREEN_H);
        scene
            .getStylesheets()
            .add(getClass().getResource("/com/sft/styles.css").toExternalForm());

        stage.setTitle("SFT");
        stage.setScene(scene);
        stage.setResizable(false); // match the fixed Pi screen during desktop testing
        boolean onPi = System.getProperty("os.name")
            .toLowerCase()
            .contains("linux");
        if (onPi) {
            stage.setFullScreen(true);
            stage.setFullScreenExitHint("");
        }
        stage.show();
    }

    // Advance the carousel by dir (+1 / -1), wrapping seamlessly at both ends.
    private void go(int dir) {
        if (animating) return;
        animating = true;
        pos += dir;
        TranslateTransition t = new TranslateTransition(SLIDE, track);
        t.setToX(-pos * VIEW_W);
        t.setOnFinished(e -> {
            // We've slid onto a clone — snap (no animation) to the real twin.
            if (pos == 0) {            // clone of last page → real last page
                pos = n;
                track.setTranslateX(-pos * VIEW_W);
            } else if (pos == n + 1) { // clone of first page → real first page
                pos = 1;
                track.setTranslateX(-pos * VIEW_W);
            }
            animating = false;
        });
        t.play();
    }

    // Animate a node's horizontal position.
    private void slide(javafx.scene.Node node, double toX) {
        TranslateTransition t = new TranslateTransition(SLIDE, node);
        t.setToX(toX);
        t.play();
    }

    private VBox page(String title, String color) {
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 64px; -fx-font-weight: bold;");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.setMinSize(VIEW_W, SCREEN_H);
        box.setPrefSize(VIEW_W, SCREEN_H);
        box.setStyle("-fx-background-color: " + color + ";");
        return box;
    }

    private VBox settingsDrawer() {
        Button close = navButton("✕"); // index 0 — wired up in start()
        VBox box = new VBox(close, new Label("Settings"));
        box.getStyleClass().add("drawer");
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefSize(SCREEN_W, SCREEN_H);
        return box;
    }

    // A thin full-height line separating a side button from the picture.
    private Region divider() {
        Region d = new Region();
        d.getStyleClass().add("divider");
        d.setMinSize(DIVIDER_W, SCREEN_H);
        d.setPrefSize(DIVIDER_W, SCREEN_H);
        d.setMaxSize(DIVIDER_W, SCREEN_H);
        return d;
    }

    private Button navButton(String glyph) {
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
