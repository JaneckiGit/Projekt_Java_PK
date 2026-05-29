package com.stockdemo.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Półprzezroczysty modal overlay, który nakłada się na cały StackPane (appRoot).
 * Zawiera wewnętrzny panel z tytułem, przyciskiem zamykania (✕) i zawartością.
 */
public class ModalOverlay extends StackPane {

    private static final Duration ANIM_DURATION = Duration.millis(180);

    private final VBox panel;
    private boolean closing = false;

    public ModalOverlay(String title, Node content) {
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);

        // === Nagłówek ===
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("modal-title");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("\u2715");
        closeBtn.getStyleClass().add("modal-close-btn");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> close());

        HBox header = new HBox(8, titleLbl, headerSpacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 18));

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #21262d;");

        // === Kontener zawartości (rozciąga się na resztę panelu) ===
        StackPane contentWrap = new StackPane(content);
        contentWrap.setAlignment(Pos.TOP_LEFT);
        contentWrap.setPadding(new Insets(0));
        VBox.setVgrow(contentWrap, Priority.ALWAYS);

        // === Wewnętrzny panel ===
        panel = new VBox(header, divider, contentWrap);
        panel.getStyleClass().add("modal-panel");
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setFillWidth(true);

        // Wewnętrzny panel ma blokować zdarzenia kliknięć
        panel.setOnMouseClicked(e -> e.consume());

        getChildren().add(panel);

        // Kliknięcie poza wewnętrznym panelem zamyka overlay
        setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                close();
            }
        });

        // Po dodaniu do sceny — przelicz wymiary panelu
        sceneProperty().addListener((obs, oldS, newS) -> {
            if (newS != null) {
                updatePanelSize(newS.getWidth(), newS.getHeight());
                newS.widthProperty().addListener((o, ov, nv) -> updatePanelSize(nv.doubleValue(), newS.getHeight()));
                newS.heightProperty().addListener((o, ov, nv) -> updatePanelSize(newS.getWidth(), nv.doubleValue()));

                // ESC zamyka modal
                newS.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                    if (ev.getCode() == KeyCode.ESCAPE && getParent() != null && !closing) {
                        close();
                        ev.consume();
                    }
                });
            }
        });
    }

    /** Aktualizuje rozmiar wewnętrznego panelu — lekko mniejszy niż okno aplikacji. */
    private void updatePanelSize(double sceneW, double sceneH) {
        double w = Math.min(sceneW - 120, 680);
        double h = Math.min(sceneH - 100, 560);
        if (w < 200) {
            w = Math.max(200, sceneW - 40);
        }
        if (h < 200) {
            h = Math.max(200, sceneH - 40);
        }
        panel.setMinWidth(w);
        panel.setPrefWidth(w);
        panel.setMaxWidth(w);
        panel.setMinHeight(h);
        panel.setPrefHeight(h);
        panel.setMaxHeight(h);
    }

    /** Dodaje overlay do podanego StackPane i odtwarza animację wejścia. */
    public void showOn(StackPane root) {
        if (root == null) {
            return;
        }
        if (!root.getChildren().contains(this)) {
            root.getChildren().add(this);
        }
        closing = false;
        toFront();
        requestFocus();

        // Stan początkowy
        setOpacity(0);
        panel.setScaleX(0.93);
        panel.setScaleY(0.93);

        FadeTransition fade = new FadeTransition(ANIM_DURATION, this);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(ANIM_DURATION, panel);
        scale.setFromX(0.93);
        scale.setFromY(0.93);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }

    /** Uruchamia animację zamknięcia i usuwa overlay z rodzica. */
    public void close() {
        if (closing) {
            return;
        }
        closing = true;

        FadeTransition fade = new FadeTransition(ANIM_DURATION, this);
        fade.setFromValue(getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(ANIM_DURATION, panel);
        scale.setFromX(panel.getScaleX());
        scale.setFromY(panel.getScaleY());
        scale.setToX(0.93);
        scale.setToY(0.93);
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, scale);
        pt.setOnFinished(e -> {
            if (getParent() instanceof Pane parent) {
                parent.getChildren().remove(this);
            }
        });
        pt.play();
    }
}
