package com.chrionline.ui;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Point d'entrée de l'application JavaFX ChriOnline.
 *
 * Démarre sur l'écran de connexion (Login.fxml).
 * La navigation entre écrans est gérée via {@link SceneManager}.
 * Utilise le thème AtlantaFX Primer Light (interface claire).
 */
public class MainApp extends Application {

    /** Largeur et hauteur par défaut de la fenêtre. */
    public static final double WIDTH  = 1024;
    public static final double HEIGHT = 680;

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        primaryStage = stage;
        stage.setTitle("ChriOnline — E-Commerce");
        stage.setMinWidth(860);
        stage.setMinHeight(580);
        stage.setResizable(true);

        SceneManager.init(stage);
        SceneManager.showLogin();

        stage.show();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
