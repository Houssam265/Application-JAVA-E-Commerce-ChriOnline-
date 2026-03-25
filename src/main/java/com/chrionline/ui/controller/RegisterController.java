package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
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
        usernameField.textProperty().addListener((obs, ov, nv) -> { ErrorHandler.clearFieldError(usernameError); ErrorHandler.clearInlineError(usernameField); updateRegisterButtonState(); });
        emailField.textProperty().addListener((obs, ov, nv) -> { ErrorHandler.clearFieldError(emailError); ErrorHandler.clearInlineError(emailField); updateRegisterButtonState(); });
        passwordField.textProperty().addListener((obs, ov, nv) -> { ErrorHandler.clearFieldError(passwordError); ErrorHandler.clearInlineError(passwordField); updateRegisterButtonState(); });
        confirmPasswordField.textProperty().addListener((obs, ov, nv) -> { ErrorHandler.clearFieldError(confirmPasswordError); ErrorHandler.clearInlineError(confirmPasswordField); updateRegisterButtonState(); });

        usernameField.focusedProperty().addListener((obs, o, focused) -> { if (!focused) validateUsernameField(); });
        emailField.focusedProperty().addListener((obs, o, focused) -> { if (!focused) validateEmailField(); });
        passwordField.focusedProperty().addListener((obs, o, focused) -> { if (!focused) validatePasswordField(); });
        confirmPasswordField.focusedProperty().addListener((obs, o, focused) -> { if (!focused) validateConfirmPasswordField(); });

        // Entrée depuis le dernier champ déclenche l'inscription
        confirmPasswordField.setOnAction(e -> handleRegister());
        updateRegisterButtonState();
    }

    // ── Gestionnaires d'événements ────────────────────────────────────────────

    /**
     * Déclenché par le bouton "Créer mon compte".
     */
    @FXML
    private void handleRegister() {
        // ① Réinitialise toutes les erreurs
        ErrorHandler.clearFieldError(usernameError);
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearFieldError(confirmPasswordError);
        ErrorHandler.clearFieldError(globalError);
        hideSuccess();

        String username        = usernameField.getText().trim();
        String email           = emailField.getText().trim();
        String password        = passwordField.getText();

        // ② Validation locale complète
        boolean valid = validateUsernameField() & validateEmailField() & validatePasswordField() & validateConfirmPasswordField();
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
                String msg = response.getMessage() == null ? "" : response.getMessage();
                String lower = msg.toLowerCase();
                if (lower.contains("username") && lower.contains("exist")) {
                    ErrorHandler.showFieldError(usernameError, "Ce nom d'utilisateur est déjà utilisé");
                    ErrorHandler.showInlineError(usernameField, "Username déjà utilisé");
                } else if (lower.contains("email") && lower.contains("exist")) {
                    ErrorHandler.showFieldError(emailError, "Cet email est déjà utilisé");
                    ErrorHandler.showInlineError(emailField, "Email déjà utilisé");
                } else if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    ErrorHandler.handleSessionExpired();
                } else {
                    ErrorHandler.showErrorDialog("Inscription échouée", msg.isBlank() ? "Inscription échouée. Veuillez réessayer." : msg);
                }
            }
        });

        registerTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = registerTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur réseau s'est produite.";
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) {
                ErrorHandler.showErrorDialog("Timeout", "La requête a expiré. Vérifiez votre connexion.");
            } else {
                ErrorHandler.showErrorDialog("Serveur indisponible", "Serveur indisponible. Vérifiez votre connexion.");
            }
            if (usernameField.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(usernameField.getScene(), this::handleRegister);
            }
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

    private void updateRegisterButtonState() {
        boolean ready = !usernameField.getText().trim().isEmpty()
                && !emailField.getText().trim().isEmpty()
                && !passwordField.getText().isBlank()
                && !confirmPasswordField.getText().isBlank();
        if (!loadingIndicator.isVisible()) registerButton.setDisable(!ready);
    }

    private boolean validateUsernameField() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (username.isEmpty()) {
            ErrorHandler.showFieldError(usernameError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(usernameField, "obligatoire");
            return false;
        }
        if (!username.matches(USERNAME_REGEX)) {
            ErrorHandler.showFieldError(usernameError, "Nom invalide (3-30, lettres/chiffres/_)");
            ErrorHandler.showInlineError(usernameField, "nom invalide");
            return false;
        }
        return true;
    }

    private boolean validateEmailField() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (email.isEmpty()) {
            ErrorHandler.showFieldError(emailError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(emailField, "obligatoire");
            return false;
        }
        if (!email.matches(EMAIL_REGEX)) {
            ErrorHandler.showFieldError(emailError, "Format email invalide (ex: nom@email.com)");
            ErrorHandler.showInlineError(emailField, "email invalide");
            return false;
        }
        return true;
    }

    private boolean validatePasswordField() {
        String password = passwordField.getText();
        if (password == null || password.isBlank()) {
            ErrorHandler.showFieldError(passwordError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(passwordField, "obligatoire");
            return false;
        }
        if (password.length() < MIN_PASSWORD) {
            ErrorHandler.showFieldError(passwordError, "Mot de passe trop court (minimum 8 caractères)");
            ErrorHandler.showInlineError(passwordField, "trop court");
            return false;
        }
        return true;
    }

    private boolean validateConfirmPasswordField() {
        String confirmPassword = confirmPasswordField.getText();
        if (confirmPassword == null || confirmPassword.isBlank()) {
            ErrorHandler.showFieldError(confirmPasswordError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(confirmPasswordField, "obligatoire");
            return false;
        }
        if (!confirmPassword.equals(passwordField.getText())) {
            ErrorHandler.showFieldError(confirmPasswordError, "Les mots de passe ne correspondent pas");
            ErrorHandler.showInlineError(confirmPasswordField, "mismatch");
            return false;
        }
        return true;
    }
}
