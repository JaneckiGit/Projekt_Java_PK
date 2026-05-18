package com.stockdemo;

import com.stockdemo.ui.MainLayout;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Application entry point.
 * We do NOT extend Application here to bypass Java 11+ module checks.
 */
public class MainApp {

    // Inner class that actually starts JavaFX
    public static class AppGUI extends Application {
        @Override
        public void start(Stage stage) {
            MainLayout root = new MainLayout();

            Scene scene = new Scene(root, 1400, 820);
            scene.getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm()
            );

            stage.setTitle("Stock Demo — Trading Platform");
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.show();
        }
    }

    public static void main(String[] args) {
        Application.launch(AppGUI.class, args);
    }
}
