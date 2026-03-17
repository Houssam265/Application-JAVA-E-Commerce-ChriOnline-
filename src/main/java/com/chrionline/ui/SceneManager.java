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

    public static void showCart() {
        loadScene("/fxml/Cart.fxml", "ChriOnline — Panier");
    }

    public static void showAdmin() {
        loadScene("/fxml/Admin.fxml", "ChriOnline — Administration");
    }

    // ── Utilitaire privé ─────────────────────────────────────────────────────

    private static void loadScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneManager.class.getResource(fxmlPath)
            );
            Parent root = loader.load();
            setSceneRoot(root, title);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[SceneManager] Impossible de charger : " + fxmlPath);
        }
    }

    public static void showProductDetail(com.chrionline.model.Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneManager.class.getResource("/fxml/ProductDetail.fxml")
            );
            Parent root = loader.load();
            
            com.chrionline.ui.controller.ProductDetailController controller = loader.getController();
            controller.setProduct(product);
            
            setSceneRoot(root, "ChriOnline — " + product.getName());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[SceneManager] Impossible de charger : /fxml/ProductDetail.fxml");
        }
    }

    private static void setSceneRoot(Parent root, String title) {
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
    }
}
