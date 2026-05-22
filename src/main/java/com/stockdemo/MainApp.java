package com.stockdemo;

import com.stockdemo.ui.MainLayout;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

//Application entry point.

public class MainApp {

    // Inner class starts JavaFX
    public static class AppGUI extends Application {
        @Override
        public void start(Stage stage) {
            MainLayout root = new MainLayout();
            StackPane appRoot = new StackPane(root);

            Scene scene = new Scene(appRoot, 1400, 820);
            scene.getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm());

            com.stockdemo.service.PortfolioPersistence.load(root.getPortfolio(), root.getMarketData());

            stage.setTitle("Stock Demo — Trading Platform");
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(600);

            stage.setOnCloseRequest(e -> {
                com.stockdemo.service.PortfolioPersistence.save(root.getPortfolio());
            });

            stage.show();
        }
    }

    public static void main(String[] args) {
        Application.launch(AppGUI.class, args);
    }
}
