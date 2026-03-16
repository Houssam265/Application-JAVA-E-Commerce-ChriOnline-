package com.chrionline.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Gestionnaire centralisé de navigation entre les scènes JavaFX.
 *
 * Tous les contrôleurs appellent les méthodes statiques ici pour
 * naviguer (ex: SceneManager.showHome() après un login réussi).
 */
public final class SceneManager {

    private static Stage stage;

    private SceneManager() {}

    public static void init(Stage primaryStage) {
        stage = primaryStage;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public static void showLogin() {
        loadScene("/fxml/Login.fxml", "ChriOnline — Connexion");
    }

    public static void showRegister() {
        loadScene("/fxml/Register.fxml", "ChriOnline — Inscription");
    }

    /**
     * Redirige vers l'écran principal (Catalogue / Home) après une connexion réussie.
     */
    public static void showHome() {
        loadScene("/fxml/Home.fxml", "ChriOnline — Accueil");
    }

    // ── Utilitaire privé ─────────────────────────────────────────────────────

    private static void loadScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneManager.class.getResource(fxmlPath)
            );
            Parent root = loader.load();

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, MainApp.WIDTH, MainApp.HEIGHT);
                scene.getStylesheets().add(
                    SceneManager.class.getResource("/css/style.css").toExternalForm()
                );
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            stage.setTitle(title);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[SceneManager] Impossible de charger : " + fxmlPath);
        }
    }
}
