package com.sft;

import java.util.List;
import javafx.application.Application;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // Pi screen size — also the width of one carousel page.
    private static final double SCREEN_W = 800;
    private static final double SCREEN_H = 400;
    private static final Duration SLIDE = Duration.millis(250);

    private final HBox track = new HBox(); // all pages laid side-by-side
    private int index = 0; // which page is centered

    @Override
    public void start(Stage stage) {
        // The carousel pages — each a full-screen VBox. Add your own here.
        List<VBox> pages = List.of(
            page("PAGE 1", "#2e3a59"),
            page("PAGE 2", "#593a2e"),
            page("PAGE 3", "#2e593a"),
            page("PAGE 4", "#592e4f")
        );
        track.getChildren().addAll(pages);

        // Plain Pane positions the track at (0,0) — a StackPane would center it.
        // Clip so off-screen pages stay hidden.
        Pane viewport = new Pane(track);
        viewport.setClip(new Rectangle(SCREEN_W, SCREEN_H));

        Button left = navButton("‹");
        Button right = navButton("›");
        Button settings = navButton("⚙");
        left.setOnAction(e -> go(pages.size(), -1));
        right.setOnAction(e -> go(pages.size(), +1));

        // Float the controls over the viewport.
        StackPane carousel = new StackPane(viewport, left, right, settings);
        StackPane.setAlignment(left, Pos.CENTER_LEFT);
        StackPane.setAlignment(right, Pos.CENTER_RIGHT);
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

    // Advance the carousel by dir (+1 / -1), wrapping around both ends.
    private void go(int count, int dir) {
        index = (index + dir + count) % count;
        slide(track, -index * SCREEN_W);
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
        box.setMinSize(SCREEN_W, SCREEN_H);
        box.setPrefSize(SCREEN_W, SCREEN_H);
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

    private Button navButton(String glyph) {
        Button b = new Button(glyph);
        b.getStyleClass().add("nav");
        return b;
    }

    public static void main(String[] args) {
        launch();
    }
}
