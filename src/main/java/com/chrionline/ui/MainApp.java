package com.chrionline.ui;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Point d'entree de l'application JavaFX ChriOnline.
 *
 * Demarre sur l'ecran de connexion (Login.fxml).
 * La navigation entre ecrans est geree via {@link SceneManager}.
 * Utilise le theme AtlantaFX Primer Light (interface claire).
 */
public class MainApp extends Application {

    /** Largeur et hauteur par defaut de la fenetre. */
    public static final double WIDTH = 1024;
    public static final double HEIGHT = 680;

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        primaryStage = stage;
        try (InputStream logoStream = MainApp.class.getResourceAsStream("/images/ChriOnline_logo.png")) {
            if (logoStream != null) {
                stage.getIcons().setAll(new Image(logoStream));
            }
        }
        stage.setTitle("ChriOnline - E-Commerce");
        stage.setMinWidth(860);
        stage.setMinHeight(580);
        stage.setResizable(true);

        SceneManager.init(stage);
        com.chrionline.ui.notifications.UdpNotificationClient.startIfNeeded();
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
