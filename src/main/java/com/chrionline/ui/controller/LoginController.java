package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

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

    // ── Frontend lockout state ────────────────────────────────────────────────
    private boolean lockoutActive = false;
    private int lockoutSecondsRemaining = 0;
    private Timeline lockoutTimeline;

    // ── Initialisation ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Synchronisation des champs
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());

        emailField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(emailError);
            ErrorHandler.clearInlineError(emailField);
            updateLoginButtonState();
        });
        passwordField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(passwordError);
            ErrorHandler.clearInlineError(passwordField);
            updateLoginButtonState();
        });
        passwordTextField.textProperty().addListener((obs, ov, nv) -> updateLoginButtonState());

        emailField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) validateEmailField();
        });
        passwordField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) validatePasswordField();
        });
        passwordTextField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) validatePasswordField();
        });

        // Permet de valider avec la touche Entrée
        passwordField.setOnAction(e -> handleLogin());
        passwordTextField.setOnAction(e -> handleLogin());
        emailField.setOnAction(e -> handleLogin());
        updateLoginButtonState();
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
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearFieldError(globalError);

        String email    = emailField.getText().trim();
        // Si le champ texte clair est visible, on prend sa valeur, sinon le passwordField
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        // ② Validation locale
        boolean valid = validateEmailField() & validatePasswordField();
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
                // ④ Connexion réussie → cache identité (userId/role) + redirection HomeScreen
                try {
                    JSONObject payload = response.getPayloadAsJsonObject();
                    ClientSession session = ClientSession.getInstance();
                    session.setUserId(payload.has("userId") ? payload.getInt("userId") : null);
                    session.setUsername(payload.optString("username", ""));
                    session.setEmail(payload.optString("email", ""));
                    String role = payload.optString("role", "CLIENT");
                    session.setRole(com.chrionline.model.User.Role.valueOf(role));
                } catch (Exception ignored) {
                    // If payload is missing or malformed, still navigate; screens will guard as needed.
                }
                Platform.runLater(SceneManager::showHome);
            } else {
                String msg = response.getMessage() == null ? "" : response.getMessage();
                if (msg.toLowerCase().contains("trop de tentatives")) {
                    int seconds = extractSecondsFromMessage(msg);
                    startLockout(seconds > 0 ? seconds : 30);
                    return;
                }
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                String lower = msg.toLowerCase();
                if (lower.contains("suspend")) {
                    ErrorHandler.showErrorDialog("Compte suspendu", "Compte suspendu. Contactez l'administrateur.");
                    return;
                }
                ErrorHandler.showErrorDialog("Connexion échouée",
                        "Identifiants incorrects. Vérifiez votre email et mot de passe.");
            }
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = loginTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur réseau s'est produite.";
            String lower = msg.toLowerCase();
            if (lower.contains("timed out") || lower.contains("timeout")) {
                ErrorHandler.showErrorDialog("Timeout", "La requête a expiré. Vérifiez votre connexion.");
                return;
            }
            ErrorHandler.showErrorDialog("Serveur indisponible", "Serveur indisponible. Vérifiez votre connexion.");
            if (emailField.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(emailField.getScene(), this::handleLogin);
            }
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

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading || lockoutActive);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void updateLoginButtonState() {
        boolean hasEmail = emailField.getText() != null && !emailField.getText().trim().isEmpty();
        String pwd = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        boolean hasPwd = pwd != null && !pwd.isBlank();
        if (!loadingIndicator.isVisible()) {
            loginButton.setDisable(lockoutActive || !(hasEmail && hasPwd));
        }
    }

    private boolean validateEmailField() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (email.isEmpty()) {
            ErrorHandler.showFieldError(emailError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(emailField, "Ce champ est obligatoire");
            return false;
        }
        if (!email.matches(EMAIL_REGEX)) {
            ErrorHandler.showFieldError(emailError, "Format email invalide (ex: nom@email.com)");
            ErrorHandler.showInlineError(emailField, "Format email invalide");
            return false;
        }
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearInlineError(emailField);
        return true;
    }

    private boolean validatePasswordField() {
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        if (password == null || password.isBlank()) {
            ErrorHandler.showFieldError(passwordError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(passwordField.isVisible() ? passwordField : passwordTextField, "Ce champ est obligatoire");
            return false;
        }
        if (password.length() < MIN_PASSWORD) {
            ErrorHandler.showFieldError(passwordError, "Mot de passe trop court (minimum 8 caractères)");
            ErrorHandler.showInlineError(passwordField.isVisible() ? passwordField : passwordTextField, "Mot de passe trop court");
            return false;
        }
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearInlineError(passwordField);
        ErrorHandler.clearInlineError(passwordTextField);
        return true;
    }

    private void startLockout(int seconds) {
        lockoutActive = true;
        lockoutSecondsRemaining = Math.max(1, seconds);
        showLockoutMessage();
        updateLoginButtonState();

        if (lockoutTimeline != null) {
            lockoutTimeline.stop();
        }
        lockoutTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            lockoutSecondsRemaining--;
            if (lockoutSecondsRemaining <= 0) {
                lockoutActive = false;
                ErrorHandler.clearFieldError(globalError);
                updateLoginButtonState();
                lockoutTimeline.stop();
                lockoutTimeline = null;
            } else {
                showLockoutMessage();
            }
        }));
        lockoutTimeline.setCycleCount(Timeline.INDEFINITE);
        lockoutTimeline.play();
    }

    private void showLockoutMessage() {
        ErrorHandler.showFieldError(globalError,
                "Trop de tentatives. Réessayez dans " + lockoutSecondsRemaining + "s.");
    }

    private int extractSecondsFromMessage(String msg) {
        String digits = msg.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
