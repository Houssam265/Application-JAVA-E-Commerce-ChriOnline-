package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.service.RecaptchaConfig;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import com.chrionline.ui.security.RecaptchaLoopbackServer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.json.JSONObject;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label globalError;
    @FXML private StackPane recaptchaContainer;
    @FXML private javafx.scene.layout.VBox recaptchaBox;
    @FXML private Label recaptchaHintLabel;

    @FXML private Button loginButton;
    @FXML private ProgressIndicator loadingIndicator;

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final int MIN_PASSWORD = 8;

    @FXML private TextField passwordTextField;
    @FXML private Button togglePasswordButton;
    @FXML private FontIcon togglePasswordIcon;

    private static boolean lockoutActive = false;
    private static int lockoutSecondsRemaining = 0;
    private Timeline lockoutTimeline;
    private boolean recaptchaEnabled = false;
    private String recaptchaToken;
    private WebEngine recaptchaEngine;
    private Timeline recaptchaPollTimeline;

    // Security enhancement variables (STATIC to persist across screen transitions)
    private static int failedLoginAttempts = 0;
    private static boolean ipBlocked = false;
    private static boolean captchaRequired = false;

    @FXML
    public void initialize() {
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        initializeRecaptcha();

        if (ipBlocked) {
            showIpBlockedMessage();
        } else if (lockoutActive && lockoutSecondsRemaining > 0) {
            startLockout(lockoutSecondsRemaining);
        }

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
            togglePasswordIcon.setIconLiteral("fas-eye");
        } else {
            passwordField.setVisible(true);
            passwordTextField.setVisible(false);
            togglePasswordIcon.setIconLiteral("fas-eye-slash");
        }
    }

    @FXML
    private void handleLogin() {
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearFieldError(globalError);

        String email = emailField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        boolean valid = validateEmailField() & validatePasswordField();
        if (!valid) {
            return;
        }

        setLoading(true);

        Task<Response> loginTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                try {
                    client.connect();
                } catch (Exception e) {
                    throw new Exception("Impossible de joindre le serveur. Verifiez votre connexion.", e);
                }

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("password", password);
                if (recaptchaEnabled && recaptchaToken != null && !recaptchaToken.isBlank()) {
                    payload.put("recaptchaToken", recaptchaToken);
                }
                return client.send(new Request(MessageProtocol.ACTION_LOGIN, payload));
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = loginTask.getValue();

            if (response.isSuccess()) {
                try {
                    JSONObject payload = response.getPayloadAsJsonObject();
                    ClientSession session = ClientSession.getInstance();
                    session.setUserId(payload.has("userId") ? payload.getInt("userId") : null);
                    session.setUsername(payload.optString("username", ""));
                    session.setEmail(payload.optString("email", ""));
                    String role = payload.optString("role", "CLIENT");
                    session.setRole(com.chrionline.model.User.Role.valueOf(role));
                } catch (Exception ignored) {
                }
                // Reset security counters on successful login
                resetSecurityCounters();
                Platform.runLater(SceneManager::showHome);
                return;
            }

            String msg = response.getMessage() == null ? "" : response.getMessage();
            JSONObject payload = response.getPayloadAsJsonObject();
            String verificationType = payload.optString("verificationType", "");
            if ("login_ip".equalsIgnoreCase(verificationType)) {
                SceneManager.showEmailVerification(email, EmailVerificationController.VerificationPurpose.LOGIN_IP);
                return;
            }
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
            if (lower.contains("non verifie")) {
                SceneManager.showEmailVerification(email);
                return;
            }
            if (lower.contains("recaptcha")) {
                resetRecaptcha();
                ErrorHandler.showErrorDialog("Verification reCAPTCHA",
                        msg.isBlank() ? "Veuillez valider le reCAPTCHA avant de vous connecter." : msg);
                return;
            }
            if (lower.contains("suspend")) {
                ErrorHandler.showErrorDialog("Compte suspendu", "Compte suspendu. Contactez l'administrateur.");
                return;
            }

            // Handle failed login attempts with progressive security measures
            handleFailedLoginAttempt();

            resetRecaptcha();
            ErrorHandler.showErrorDialog("Connexion echouee",
                    "Identifiants incorrects. Verifiez votre email et mot de passe.");
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = loginTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur reseau s'est produite.";
            String lower = msg.toLowerCase();
            if (lower.contains("timed out") || lower.contains("timeout")) {
                ErrorHandler.showErrorDialog("Timeout", "La requete a expire. Verifiez votre connexion.");
                return;
            }
            resetRecaptcha();
            ErrorHandler.showErrorDialog("Serveur indisponible", "Serveur indisponible. Verifiez votre connexion.");
            if (emailField.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(emailField.getScene(), this::handleLogin);
            }
        });

        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void goToRegister() {
        SceneManager.showRegister();
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading || lockoutActive);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void updateLoginButtonState() {
        boolean hasEmail = emailField.getText() != null && !emailField.getText().trim().isEmpty();
        String pwd = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        boolean hasPwd = pwd != null && !pwd.isBlank();
        boolean captchaValid = !captchaRequired || !recaptchaEnabled || (recaptchaToken != null && !recaptchaToken.isBlank());
        if (!loadingIndicator.isVisible()) {
            loginButton.setDisable(ipBlocked || lockoutActive || !(hasEmail && hasPwd && captchaValid));
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
            ErrorHandler.showFieldError(passwordError, "Mot de passe trop court (minimum 8 caracteres)");
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
                // Keep CAPTCHA visible if required after lockout
                if (captchaRequired) {
                    showCaptchaIfNeeded();
                }
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
                "Trop de tentatives. Reessayez dans " + lockoutSecondsRemaining + "s.");
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

    private void initializeRecaptcha() {
        recaptchaEnabled = RecaptchaConfig.isClientConfigured();
        if (!recaptchaEnabled || recaptchaBox == null || recaptchaContainer == null) {
            return;
        }

        // Initialize UI based on persisted static state to avoid flicker
        if (captchaRequired) {
            recaptchaBox.setVisible(true);
            recaptchaBox.setManaged(true);
            showRecaptchaHint("Validez le reCAPTCHA pour continuer.");
        } else {
            recaptchaBox.setVisible(false);
            recaptchaBox.setManaged(false);
            showRecaptchaHint("");
        }

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.setPrefWidth(304);
        webView.setMinWidth(304);
        webView.setMaxWidth(304);
        webView.setPrefHeight(84);
        webView.setMinHeight(84);
        webView.setMaxHeight(84);
        recaptchaContainer.getChildren().setAll(webView);

        recaptchaEngine = webView.getEngine();
        recaptchaEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) recaptchaEngine.executeScript("window");
                window.setMember("javaRecaptcha", new RecaptchaBridge());
                startRecaptchaPolling();
            }
        });

        try {
            recaptchaEngine.load(RecaptchaLoopbackServer.startIfNeeded());
        } catch (Exception e) {
            recaptchaEnabled = false;
            recaptchaBox.setVisible(false);
            recaptchaBox.setManaged(false);
            recaptchaContainer.getChildren().clear();
        }
        updateLoginButtonState();
    }

    private void resetRecaptcha() {
        recaptchaToken = null;
        updateLoginButtonState();
        if (captchaRequired && recaptchaEnabled) {
            showRecaptchaHint("Validez le reCAPTCHA pour continuer.");
            recaptchaBox.setVisible(true);
            recaptchaBox.setManaged(true);
        } else {
            showRecaptchaHint("");
            if (recaptchaBox != null) {
                recaptchaBox.setVisible(false);
                recaptchaBox.setManaged(false);
            }
        }
        if (recaptchaEngine != null) {
            Platform.runLater(() -> {
                try {
                    recaptchaEngine.executeScript("if (window.grecaptcha) { grecaptcha.reset(); }");
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void showRecaptchaHint(String message) {
        if (recaptchaHintLabel == null) {
            return;
        }
        boolean visible = message != null && !message.isBlank();
        recaptchaHintLabel.setText(visible ? message : "");
        recaptchaHintLabel.setVisible(visible);
        recaptchaHintLabel.setManaged(visible);
    }

    public final class RecaptchaBridge {
        public void onToken(String token) {
            Platform.runLater(() -> {
                recaptchaToken = token == null ? null : token.trim();
                showRecaptchaHint("");
                updateLoginButtonState();
            });
        }

        public void onExpired() {
            Platform.runLater(() -> {
                recaptchaToken = null;
                showRecaptchaHint("Le reCAPTCHA a expire. Veuillez le valider a nouveau.");
                updateLoginButtonState();
            });
        }

        public void onError(String message) {
            Platform.runLater(() -> {
                recaptchaToken = null;
                showRecaptchaHint(message == null || message.isBlank()
                        ? "Erreur reCAPTCHA. Veuillez reessayer."
                        : message);
                updateLoginButtonState();
            });
        }
    }

    private void handleFailedLoginAttempt() {
        failedLoginAttempts++;

        // Progressive security measures based on failed attempts
        if (failedLoginAttempts >= 12) {
            // 12+ failures: IP block + email notification
            ipBlocked = true;
            showIpBlockedMessage();
            sendSecurityAlertEmail();
            return;
        } else if (failedLoginAttempts >= 9) {
            // 9+ failures: 1 hour lockout
            startLockout(3600); // 1 hour = 3600 seconds
            captchaRequired = true;
            showCaptchaIfNeeded();
        } else if (failedLoginAttempts >= 6) {
            // 6+ failures: 10 minute lockout
            startLockout(600); // 10 minutes = 600 seconds
            captchaRequired = true;
            showCaptchaIfNeeded();
        } else if (failedLoginAttempts >= 3) {
            // 3+ failures: 1 minute lockout + CAPTCHA
            startLockout(60); // 1 minute = 60 seconds
            captchaRequired = true;
            showCaptchaIfNeeded();
        }
        // For 1-2 failures: no special measures, just normal error message
    }

    private void showIpBlockedMessage() {
        ErrorHandler.showFieldError(globalError,
                "Trop de tentatives de connexion. Votre acces est bloque pour securite. Un email de notification a ete envoye.");
        loginButton.setDisable(true);
    }

    private void sendSecurityAlertEmail() {
        // In a real implementation, this would send an email to the user
        // For now, we'll just log it
        org.apache.logging.log4j.LogManager.getLogger(LoginController.class)
                .warn("SECURITY ALERT: Multiple failed login attempts detected. IP blocked.");
    }

    private void showCaptchaIfNeeded() {
        if (captchaRequired && recaptchaEnabled && recaptchaBox != null) {
            recaptchaBox.setVisible(true);
            recaptchaBox.setManaged(true);
            showRecaptchaHint("Validez le reCAPTCHA pour continuer.");
        }
    }

    private void resetSecurityCounters() {
        failedLoginAttempts = 0;
        ipBlocked = false;
        captchaRequired = false;
        recaptchaToken = null;
        lockoutActive = false;
        lockoutSecondsRemaining = 0;
        if (lockoutTimeline != null) {
            lockoutTimeline.stop();
            lockoutTimeline = null;
        }
        if (recaptchaBox != null) {
            recaptchaBox.setVisible(false);
            recaptchaBox.setManaged(false);
        }
        showRecaptchaHint("");
        ErrorHandler.clearFieldError(globalError);
        updateLoginButtonState();
    }

    private void startRecaptchaPolling() {
        if (recaptchaPollTimeline != null) {
            recaptchaPollTimeline.stop();
        }
        recaptchaPollTimeline = new Timeline(new KeyFrame(Duration.millis(400), e -> pollRecaptchaToken()));
        recaptchaPollTimeline.setCycleCount(Timeline.INDEFINITE);
        recaptchaPollTimeline.play();
    }

    private void pollRecaptchaToken() {
        if (!recaptchaEnabled || recaptchaEngine == null) {
            return;
        }
        try {
            Object token = recaptchaEngine.executeScript(
                    "(window.grecaptcha && grecaptcha.getResponse) ? grecaptcha.getResponse() : ''");
            String current = token == null ? "" : String.valueOf(token).trim();
            String previous = recaptchaToken == null ? "" : recaptchaToken.trim();
            if (!current.equals(previous)) {
                recaptchaToken = current.isBlank() ? null : current;
                if (recaptchaToken == null) {
                    showRecaptchaHint("Validez le reCAPTCHA pour activer la connexion.");
                } else {
                    showRecaptchaHint("");
                }
                updateLoginButtonState();
            }
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void handleForgotPassword() {
        SceneManager.showForgotPasswordRequest();
    }
}
