package com.chrionline.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ErrorHandler {
    private ErrorHandler() {}

    private static final Map<Scene, BannerState> BANNERS = new ConcurrentHashMap<>();

    public static void showFieldError(Label errorLabel, String message) {
        if (errorLabel == null) return;
        String text = "⚠ " + (message == null ? "" : message);
        errorLabel.setText(text);
        errorLabel.setStyle("-fx-text-fill: #FF4444; -fx-font-size: 11px; -fx-font-weight: 600;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        errorLabel.setOpacity(0);
        Timeline in = new Timeline(new KeyFrame(javafx.util.Duration.millis(200),
                new KeyValue(errorLabel.opacityProperty(), 1, Interpolator.EASE_OUT)));
        in.play();
    }

    public static void clearFieldError(Label errorLabel) {
        if (errorLabel == null || !errorLabel.isVisible()) return;
        Timeline out = new Timeline(new KeyFrame(javafx.util.Duration.millis(160),
                new KeyValue(errorLabel.opacityProperty(), 0, Interpolator.EASE_IN)));
        out.setOnFinished(e -> {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            errorLabel.setOpacity(1);
        });
        out.play();
    }

    public static void showInlineError(Node targetNode, String message) {
        if (targetNode == null) return;
        String style = targetNode.getStyle() == null ? "" : targetNode.getStyle();
        if (!style.contains("-fx-border-color: #FF4444")) {
            targetNode.setStyle(style + "; -fx-border-color: #FF4444; -fx-border-width: 1.8px;");
        }
        TranslateTransition shake = new TranslateTransition(javafx.util.Duration.millis(55), targetNode);
        shake.setFromX(-6);
        shake.setToX(6);
        shake.setAutoReverse(true);
        shake.setCycleCount(6); // 3 shakes
        shake.setOnFinished(e -> targetNode.setTranslateX(0));
        shake.play();
    }

    public static void clearInlineError(Node targetNode) {
        if (targetNode == null) return;
        String style = targetNode.getStyle();
        if (style == null) return;
        targetNode.setStyle(style
                .replace("; -fx-border-color: #FF4444; -fx-border-width: 1.8px;", "")
                .replace("-fx-border-color: #FF4444; -fx-border-width: 1.8px;", ""));
    }

    public static void showErrorDialog(String title, String message) {
        showDialog(title, message, Color.web("#ef4444"), "❌");
    }

    public static void showInfoDialog(String title, String message) {
        showDialog(title, message, Color.web("#10b981"), "✅");
    }

    public static void showWarningDialog(String title, String message) {
        showDialog(title, message, Color.web("#f59e0b"), "⚠");
    }

    private static void showDialog(String title, String message, Color accent, String icon) {
        Platform.runLater(() -> {
            Stage owner = MainApp.getPrimaryStage();
            Stage s = new Stage(StageStyle.TRANSPARENT);
            s.initOwner(owner);
            s.initModality(Modality.APPLICATION_MODAL);
            s.setTitle(title == null ? "Erreur" : title);

            BorderPane card = new BorderPane();
            // Carte pro (theme clair): fond blanc + bordure légère + ombre.
            card.setStyle("-fx-background-color: rgba(255,255,255,0.99)"
                    + "; -fx-background-radius: 16px;"
                    + "; -fx-border-color: rgba(15, 23, 42, 0.12)"
                    + "; -fx-border-width: 1px;"
                    + "; -fx-border-radius: 16px;"
                    + "; -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.14), 26, 0.18, 0, 10);");
            card.setPrefWidth(460);

            // Accents (exigence: barre à gauche)
            card.setLeft(new StackPane() {{
                setStyle("-fx-background-color: " + toRgba(accent) + ";"
                        + " -fx-background-radius: 16px 0 0 16px;");
                setPrefWidth(6);
            }});

            // En-tête avec pastille icône
            Label iconLbl = new Label(icon == null ? "" : icon);
            iconLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 900;");
            StackPane iconBubble = new StackPane(iconLbl);
            iconBubble.setPrefSize(34, 34);
            iconBubble.setStyle("-fx-background-color: " + toRgba(accent) + ";"
                    + " -fx-background-radius: 999px;");

            Label titleLbl = new Label(title == null ? "" : title);
            titleLbl.setStyle("-fx-text-fill: #0f172a;"
                    + " -fx-font-size: 16px;"
                    + " -fx-font-weight: 900;");

            HBox header = new HBox(12, iconBubble, titleLbl);
            header.setAlignment(Pos.CENTER_LEFT);

            Label msgLbl = new Label(message == null ? "" : message);
            msgLbl.setWrapText(true);
            msgLbl.setStyle("-fx-text-fill: #334155;"
                    + " -fx-font-size: 13px;"
                    + " -fx-font-weight: 600;");

            Button ok = new Button("OK");
            ok.setStyle("-fx-background-color: linear-gradient(to right, #f46a3d, #fb923c);"
                    + " -fx-text-fill: white;"
                    + " -fx-font-weight: 800;"
                    + " -fx-background-radius: 12px;"
                    + " -fx-effect: dropshadow(gaussian, rgba(244, 106, 61, 0.35), 12, 0, 0, 2);"
                    + " -fx-padding: 10 22 10 22;");
            ok.setOnAction(e -> s.close());

            HBox actions = new HBox(ok);
            actions.setAlignment(Pos.CENTER_RIGHT);

            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, header, msgLbl, actions);
            content.setPadding(new Insets(16, 18, 16, 18));
            card.setCenter(content);

            StackPane root = new StackPane(card);
            root.setPadding(new Insets(14));
            // Voile léger: plus sombre que l'avant pour améliorer la lisibilité.
            root.setStyle("-fx-background-color: rgba(15, 23, 42, 0.26);");

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            s.setScene(scene);
            s.showAndWait();
        });
    }

    public static void showServerUnavailableBanner(Scene scene, Runnable retryAction) {
        if (scene == null || retryAction == null) return;
        Platform.runLater(() -> {
            BannerState st = BANNERS.computeIfAbsent(scene, k -> createBannerState(scene));
            st.remainingSeconds = 10;
            st.label.setText("⚠ Serveur indisponible — Nouvelle tentative dans 10s...");
            if (!st.container.isVisible()) {
                st.container.setVisible(true);
                st.container.setManaged(true);
                st.container.setTranslateY(-56);
                new Timeline(new KeyFrame(javafx.util.Duration.millis(220),
                        new KeyValue(st.container.translateYProperty(), 0, Interpolator.EASE_OUT))).play();
            }
            if (st.timeline != null) st.timeline.stop();
            st.timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
                st.remainingSeconds--;
                if (st.remainingSeconds <= 0) {
                    if (st.timeline != null) st.timeline.stop();
                    hideBanner(st);
                    retryAction.run();
                } else {
                    st.label.setText("⚠ Serveur indisponible — Nouvelle tentative dans " + st.remainingSeconds + "s...");
                }
            }));
            st.timeline.setCycleCount(10);
            st.timeline.play();
        });
    }

    private static BannerState createBannerState(Scene scene) {
        BannerState st = new BannerState();
        st.label = new Label();
        st.label.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 800;");
        HBox bar = new HBox(st.label);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setStyle("-fx-background-color: #b91c1c;");
        HBox.setHgrow(st.label, Priority.ALWAYS);
        st.container = new StackPane(bar);
        st.container.setVisible(false);
        st.container.setManaged(false);
        StackPane.setAlignment(st.container, Pos.TOP_CENTER);

        Node root = scene.getRoot();
        if (root instanceof StackPane sp) {
            sp.getChildren().add(st.container);
        } else {
            StackPane wrapper = new StackPane(root, st.container);
            scene.setRoot(wrapper);
        }
        return st;
    }

    private static void hideBanner(BannerState st) {
        Timeline out = new Timeline(new KeyFrame(javafx.util.Duration.millis(180),
                new KeyValue(st.container.translateYProperty(), -56, Interpolator.EASE_IN)));
        out.setOnFinished(e -> {
            st.container.setVisible(false);
            st.container.setManaged(false);
            st.container.setTranslateY(0);
        });
        out.play();
    }

    public static boolean isSessionExpiredMessage(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("expired session") || m.contains("invalid or expired session") || m.contains("session expir");
    }

    public static void handleSessionExpired() {
        showWarningDialog("Session expirée", "Session expirée. Veuillez vous reconnecter.");
        Platform.runLater(SceneManager::showLogin);
    }

    private static String toRgba(Color c) {
        return String.format("rgba(%d,%d,%d,%.2f)",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                c.getOpacity());
    }

    private static final class BannerState {
        private StackPane container;
        private Label label;
        private Timeline timeline;
        private int remainingSeconds = 10;
    }
}

