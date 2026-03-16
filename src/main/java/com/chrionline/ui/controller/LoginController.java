package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.json.JSONObject;

/**
 * Contrôleur de l'écran de connexion (Login.fxml).
 *
 * Responsabilités (KAN-5) :
 *  1. Validation locale des champs avant envoi (format email, longueur mot de passe)
 *  2. Envoi de la requête LOGIN via Socket TCP dans un Thread dédié
 *  3. Affichage des messages d'erreur clairs (champ par champ + erreur globale serveur)
 *  4. Redirection vers HomeScreen après connexion réussie
 */
public class LoginController {

    // ── Références FXML ──────────────────────────────────────────────────────
    @FXML private TextField         emailField;
    @FXML private PasswordField     passwordField;

    @FXML private Label             emailError;
    @FXML private Label             passwordError;
    @FXML private Label             globalError;

    @FXML private Button            loginButton;
    @FXML private ProgressIndicator loadingIndicator;

    // ── Regex de validation ───────────────────────────────────────────────────
    private static final String EMAIL_REGEX   = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final int    MIN_PASSWORD  = 8;

    // ── Initialisation ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Efface les erreurs au focus sur le champ
        emailField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) hideError(emailError);
        });
        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) hideError(passwordError);
        });
        passwordTextField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) hideError(passwordError);
        });

        // Synchronisation des champs
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());

        // Permet de valider avec la touche Entrée
        passwordField.setOnAction(e -> handleLogin());
        passwordTextField.setOnAction(e -> handleLogin());
        emailField.setOnAction(e -> handleLogin());
    }

    @FXML
    private void togglePasswordVisibility() {
        if (passwordField.isVisible()) {
            passwordField.setVisible(false);
            passwordTextField.setVisible(true);
            togglePasswordButton.setText("🔒"); // Ou oeil barré
        } else {
            passwordField.setVisible(true);
            passwordTextField.setVisible(false);
            togglePasswordButton.setText("👁");
        }
    }

    // ── Champs Mot de passe ───────────────────────────────────────────────────
    @FXML private TextField         passwordTextField;
    @FXML private Button            togglePasswordButton;

    /**
     * Déclenché par le bouton "Se connecter" ou la touche Entrée.
     * Valide d'abord localement, puis envoie via TCP dans un thread séparé.
     */
    @FXML
    private void handleLogin() {
        // ① Réinitialise les erreurs
        hideError(emailError);
        hideError(passwordError);
        hideError(globalError);

        String email    = emailField.getText().trim();
        // Si le champ texte clair est visible, on prend sa valeur, sinon le passwordField
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        // ② Validation locale
        boolean valid = true;

        if (email.isEmpty()) {
            showError(emailError, "L'adresse email est requise.");
            valid = false;
        } else if (!email.matches(EMAIL_REGEX)) {
            showError(emailError, "Format d'email invalide (ex: user@email.com).");
            valid = false;
        }

        if (password.isEmpty()) {
            showError(passwordError, "Le mot de passe est requis.");
            valid = false;
        } else if (password.length() < MIN_PASSWORD) {
            showError(passwordError, "Le mot de passe doit contenir au moins 8 caractères.");
            valid = false;
        }

        if (!valid) return;

        // ③ Envoi TCP dans un thread JavaFX (Task) pour ne pas bloquer l'UI
        setLoading(true);

        Task<Response> loginTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();

                // Connexion au serveur si nécessaire
                try {
                    client.connect();
                } catch (Exception e) {
                    throw new Exception("Impossible de joindre le serveur. Vérifiez votre connexion.", e);
                }

                // Construction du payload JSON selon le protocole
                JSONObject payload = new JSONObject();
                payload.put("email",    email);
                payload.put("password", password);

                Request request = new Request(MessageProtocol.ACTION_LOGIN, payload);
                return client.send(request);
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = loginTask.getValue();

            if (response.isSuccess()) {
                // ④ Connexion réussie → redirection HomeScreen
                javafx.application.Platform.runLater(SceneManager::showHome);
            } else {
                // ⑤ Erreur métier renvoyée par le serveur
                showError(globalError, response.getMessage().isEmpty()
                        ? "Identifiants incorrects. Veuillez réessayer."
                        : response.getMessage());
            }
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = loginTask.getException();
            showError(globalError, cause != null
                    ? cause.getMessage()
                    : "Une erreur réseau s'est produite.");
        });

        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    /** Navigation vers l'écran d'inscription. */
    @FXML
    private void goToRegister() {
        SceneManager.showRegister();
    }

    // ── Utilitaires privés ────────────────────────────────────────────────────

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideError(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }
}
