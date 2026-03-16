package com.chrionline.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Point d'entrée de l'application JavaFX ChriOnline.
 *
 * Démarre sur l'écran de connexion (Login.fxml).
 * La navigation entre écrans est gérée via {@link SceneManager}.
 */
public class MainApp extends Application {

    /** Largeur et hauteur par défaut de la fenêtre. */
    public static final double WIDTH  = 960;
    public static final double HEIGHT = 640;

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        stage.setTitle("ChriOnline — E-Commerce");
        stage.setMinWidth(800);
        stage.setMinHeight(550);
        stage.setResizable(true);

        // Écran initial : connexion
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
