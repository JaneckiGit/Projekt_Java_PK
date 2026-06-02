package com.stockdemo.ui;

import com.stockdemo.analyzer.AnalysisResult;
import com.stockdemo.analyzer.AnalyzerService;
import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/**
 * In-app Analyzer result content displayed inside the shared modal overlay.
 */
public class AnalyzerWindow extends VBox {

    private static final String BUY_COLOR = "#3fb950";
    private static final String SELL_COLOR = "#f85149";
    private static final String NEUTRAL_COLOR = "#8b949e";

    /** Builds Analyzer modal content for the current candle series and instrument. */
    public AnalyzerWindow(List<Candle> candles, Instrument instrument) {
        AnalysisResult result = new AnalyzerService().analyze(candles, instrument);

        setSpacing(14);
        setPadding(new Insets(18));
        setFillWidth(true);

        Label signalLabel = new Label(result.signal().name());
        signalLabel.setMinWidth(96);
        signalLabel.setAlignment(Pos.CENTER);
        signalLabel.setStyle(signalLabelStyle(signalColor(result.signal())));

        Label confidenceLabel = new Label(String.format(Locale.US, "%.0f%% confidence", result.confidence() * 100));
        confidenceLabel.getStyleClass().add("modal-title");
        confidenceLabel.setAlignment(Pos.CENTER_RIGHT);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(12, signalLabel, topSpacer, confidenceLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        DoubleProperty confidenceValue = new SimpleDoubleProperty(result.confidence());
        ProgressBar confidenceBar = new ProgressBar();
        confidenceBar.setMaxWidth(Double.MAX_VALUE);
        confidenceBar.progressProperty().bind(confidenceValue);
        confidenceBar.setStyle("-fx-accent: " + signalColor(result.signal()) + ";");

        Label patternsLabel = new Label("Detected Patterns:");
        patternsLabel.getStyleClass().add("modal-title");

        FlowPane patternBadges = new FlowPane(6, 6);
        List<String> patterns = result.detectedPatterns();
        if (patterns.isEmpty()) {
            patternBadges.getChildren().add(createBadge("None", NEUTRAL_COLOR));
        } else {
            for (String pattern : patterns) {
                patternBadges.getChildren().add(createBadge(pattern, "#58a6ff"));
            }
        }

        TextArea summary = new TextArea(result.summary());
        summary.setEditable(false);
        summary.setWrapText(true);
        summary.setMinHeight(80);
        summary.setPrefRowCount(4);
        summary.getStyleClass().add("trade-field");
        summary.setStyle(themedSummaryStyle());
        VBox.setVgrow(summary, Priority.ALWAYS);

        getChildren().addAll(
                top,
                confidenceBar,
                new Separator(),
                patternsLabel,
                patternBadges,
                new Separator(),
                summary
        );
    }

    private static Label createBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setStyle(
                "-fx-background-color: rgba(88,166,255,0.12);"
                        + " -fx-border-color: " + color + ";"
                        + " -fx-border-radius: 10;"
                        + " -fx-background-radius: 10;"
                        + " -fx-text-fill: " + color + ";"
                        + " -fx-font-size: 11px;"
                        + " -fx-font-weight: 700;"
                        + " -fx-padding: 4 8;"
        );
        return badge;
    }

    private static String signalColor(AnalysisResult.Signal signal) {
        return switch (signal) {
            case BUY -> BUY_COLOR;
            case SELL -> SELL_COLOR;
            case NEUTRAL -> NEUTRAL_COLOR;
        };
    }

    private static String signalLabelStyle(String color) {
        return "-fx-background-color: " + color + ";"
                + " -fx-background-radius: 6;"
                + " -fx-text-fill: white;"
                + " -fx-font-size: 13px;"
                + " -fx-font-weight: 800;"
                + " -fx-padding: 7 12;";
    }

    private static String themedSummaryStyle() {
        return MainLayout.isDarkTheme()
                ? "-fx-control-inner-background: #0d1117; -fx-text-fill: #e6edf3;"
                : "-fx-control-inner-background: #ffffff; -fx-text-fill: #1f2328;";
    }
}
