package com.chrionline.ui;

import com.chrionline.model.Product;
import com.chrionline.ui.controller.EmailVerificationController;
import com.chrionline.ui.controller.ProductDetailController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class SceneManager {

    private static Stage stage;

    private SceneManager() {}

    public static void init(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void showLogin() {
        loadScene("/fxml/Login.fxml", "ChriOnline - Connexion");
    }

    public static void showRegister() {
        loadScene("/fxml/Register.fxml", "ChriOnline - Inscription");
    }

    public static void showForgotPasswordRequest() {
        loadScene("/fxml/ForgotPasswordRequest.fxml", "ChriOnline - Forgot Password");
    }

    public static void showResetPassword() {
        loadScene("/fxml/ResetPassword.fxml", "ChriOnline - Reset Password");
    }

    public static void showEmailVerification(String email) {
        showEmailVerification(email, EmailVerificationController.VerificationPurpose.ACCOUNT_EMAIL);
    }

    public static void showEmailVerification(String email, EmailVerificationController.VerificationPurpose purpose) {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource("/fxml/EmailVerification.fxml"));
            Parent root = loader.load();

            EmailVerificationController controller = loader.getController();
            controller.configure(email, purpose);

            setSceneRoot(root, "ChriOnline - Verification Email");
        } catch (Exception e) {
            e.printStackTrace();
            ErrorHandler.showErrorDialog(
                    "Navigation impossible",
                    "Impossible d'ouvrir l'ecran de verification email.\n" + e.getMessage()
            );
        }
    }

    public static void showHome() {
        loadScene("/fxml/Home.fxml", "ChriOnline - Accueil");
    }

    public static void showCart() {
        loadScene("/fxml/Cart.fxml", "ChriOnline - Panier");
    }

    public static void showCheckout() {
        loadScene("/fxml/Checkout.fxml", "ChriOnline - Checkout");
    }

    public static void showOrderHistory() {
        loadScene("/fxml/OrderHistory.fxml", "ChriOnline - Mes commandes");
    }

    public static void showProfile() {
        loadScene("/fxml/Profile.fxml", "ChriOnline - Profil");
    }

    public static void showAdmin() {
        loadScene("/fxml/Admin.fxml", "ChriOnline - Administration");
    }

    private static void loadScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource(fxmlPath));
            Parent root = loader.load();
            setSceneRoot(root, title);
        } catch (Exception e) {
            e.printStackTrace();
            ErrorHandler.showErrorDialog(
                    "Navigation impossible",
                    "Impossible de charger l'ecran " + fxmlPath + ".\n" + e.getMessage()
            );
        }
    }

    public static void showProductDetail(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource("/fxml/ProductDetail.fxml"));
            Parent root = loader.load();

            ProductDetailController controller = loader.getController();
            controller.setProduct(product);

            setSceneRoot(root, "ChriOnline - " + product.getName());
        } catch (Exception e) {
            e.printStackTrace();
            ErrorHandler.showErrorDialog(
                    "Navigation impossible",
                    "Impossible d'ouvrir le detail du produit.\n" + e.getMessage()
            );
        }
    }

    private static URL requireResource(String path) throws IOException {
        URL resource = SceneManager.class.getResource(path);
        if (resource == null) {
            throw new IOException("Ressource introuvable: " + path);
        }
        return resource;
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
