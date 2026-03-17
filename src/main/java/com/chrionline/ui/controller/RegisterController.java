package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.json.JSONObject;

/**
 * Contrôleur de l'écran d'inscription (Register.fxml).
 *
 * Responsabilités (KAN-5) :
 *  1. Validation complète des champs (username non vide, email, MDP ≥ 8 chars, confirmation)
 *  2. Envoi de la requête REGISTER via Socket TCP dans un Thread dédié
 *  3. Affichage des messages d'erreur clairs (par champ + erreur globale serveur)
 *  4. Affichage d'un message de succès puis redirection vers LoginScreen
 */
public class RegisterController {

    // ── Références FXML ──────────────────────────────────────────────────────
    @FXML private TextField         usernameField;
    @FXML private TextField         emailField;
    @FXML private PasswordField     passwordField;
    @FXML private PasswordField     confirmPasswordField;

    @FXML private Label             usernameError;
    @FXML private Label             emailError;
    @FXML private Label             passwordError;
    @FXML private Label             confirmPasswordError;
    @FXML private Label             globalError;
    @FXML private Label             successMessage;

    @FXML private Button            registerButton;
    @FXML private ProgressIndicator loadingIndicator;

    // ── Regex de validation ───────────────────────────────────────────────────
    private static final String EMAIL_REGEX    = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String USERNAME_REGEX = "^[A-Za-z0-9_]{3,30}$";
    private static final int    MIN_PASSWORD   = 8;

    // ── Initialisation ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Efface l'erreur d'un champ quand l'utilisateur le sélectionne
        usernameField.focusedProperty().addListener((obs, o, n) -> { if (n) hideError(usernameError); });
        emailField.focusedProperty().addListener((obs, o, n) -> { if (n) hideError(emailError); });
        passwordField.focusedProperty().addListener((obs, o, n) -> { if (n) hideError(passwordError); });
        confirmPasswordField.focusedProperty().addListener((obs, o, n) -> { if (n) hideError(confirmPasswordError); });

        // Entrée depuis le dernier champ déclenche l'inscription
        confirmPasswordField.setOnAction(e -> handleRegister());
    }

    // ── Gestionnaires d'événements ────────────────────────────────────────────

    /**
     * Déclenché par le bouton "Créer mon compte".
     */
    @FXML
    private void handleRegister() {
        // ① Réinitialise toutes les erreurs
        hideError(usernameError);
        hideError(emailError);
        hideError(passwordError);
        hideError(confirmPasswordError);
        hideError(globalError);
        hideSuccess();

        String username        = usernameField.getText().trim();
        String email           = emailField.getText().trim();
        String password        = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // ② Validation locale complète
        boolean valid = true;

        if (username.isEmpty()) {
            showError(usernameError, "Le nom d'utilisateur est requis.");
            valid = false;
        } else if (!username.matches(USERNAME_REGEX)) {
            showError(usernameError, "3 à 30 caractères : lettres, chiffres, underscore uniquement.");
            valid = false;
        }

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

        if (confirmPassword.isEmpty()) {
            showError(confirmPasswordError, "Veuillez confirmer votre mot de passe.");
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            showError(confirmPasswordError, "Les mots de passe ne correspondent pas.");
            valid = false;
        }

        if (!valid) return;

        // ③ Envoi TCP dans un thread séparé
        setLoading(true);

        Task<Response> registerTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();

                try {
                    client.connect();
                } catch (Exception e) {
                    throw new Exception("Impossible de joindre le serveur. Vérifiez votre connexion.", e);
                }

                // Payload selon le protocole ChriOnline
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("email",    email);
                payload.put("password", password);

                Request request = new Request(MessageProtocol.ACTION_REGISTER, payload);
                return client.send(request);
            }
        };

        registerTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = registerTask.getValue();

            if (response.isSuccess()) {
                // Cache identité côté client (le serveur crée aussi une session)
                try {
                    JSONObject payload = response.getPayloadAsJsonObject();
                    ClientSession session = ClientSession.getInstance();
                    session.setUserId(payload.has("userId") ? payload.getInt("userId") : null);
                    session.setUsername(payload.optString("username", ""));
                    session.setEmail(payload.optString("email", ""));
                    String role = payload.optString("role", "CLIENT");
                    session.setRole(com.chrionline.model.User.Role.valueOf(role));
                } catch (Exception ignored) {}

                // ④ Inscription réussie : message de succès, puis redirige vers Login
                showSuccess("Compte créé avec succès ! Redirection vers la connexion...");

                // Délai de 2 secondes pour laisser l'utilisateur lire le message
                Task<Void> delay = new Task<>() {
                    @Override protected Void call() throws Exception {
                        Thread.sleep(2000);
                        return null;
                    }
                };
                delay.setOnSucceeded(e -> javafx.application.Platform.runLater(SceneManager::showLogin));
                Thread t = new Thread(delay);
                t.setDaemon(true);
                t.start();

            } else {
                // ⑤ Erreur renvoyée par le serveur (ex: email déjà utilisé)
                showError(globalError, response.getMessage().isEmpty()
                        ? "Inscription échouée. Veuillez réessayer."
                        : response.getMessage());
            }
        });

        registerTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = registerTask.getException();
            showError(globalError, cause != null
                    ? cause.getMessage()
                    : "Une erreur réseau s'est produite.");
        });

        Thread thread = new Thread(registerTask);
        thread.setDaemon(true);
        thread.start();
    }

    /** Navigation vers l'écran de connexion. */
    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
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

    private void showSuccess(String message) {
        successMessage.setText(message);
        successMessage.setVisible(true);
        successMessage.setManaged(true);
    }

    private void hideSuccess() {
        successMessage.setVisible(false);
        successMessage.setManaged(false);
    }

    private void setLoading(boolean loading) {
        registerButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }
}
