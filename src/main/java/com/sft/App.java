package com.sft;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        Label hello = new Label("SFT - Hello");
        StackPane root = new StackPane(hello); // container holds UI

        Scene scene = new Scene(root, 800, 400); // 800x400 pi screen
        scene
            .getStylesheets()
            .add(
                getClass().getResource("/com/sft/styles.css").toExternalForm()
            ); // pull from stylesheet

        stage.setTitle("SFT");
        stage.setScene(scene);
        // only full screen on the pi
        boolean onPi = System.getProperty("os.name")
            .toLowerCase()
            .contains("linux");
        if (onPi) {
            stage.setFullScreen(true);
            stage.setFullScreenExitHint(""); // hides the "Press ESC to exit" overlay
        }

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
