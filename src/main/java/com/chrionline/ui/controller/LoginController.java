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
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.json.JSONObject;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Random;

public class LoginController {

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final int MIN_PASSWORD = 8;

    private static boolean lockoutActive = false;
    private static int lockoutSecondsRemaining = 0;
    private static int failedLoginAttempts = 0;
    private static boolean ipBlocked = false;
    private static boolean captchaRequired = false;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;
    @FXML private Button togglePasswordButton;
    @FXML private FontIcon togglePasswordIcon;

    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label globalError;

    @FXML private StackPane captchaContainer;
    @FXML private javafx.scene.layout.VBox captchaBox;
    @FXML private javafx.scene.layout.VBox captchaPanel;
    @FXML private Label captchaHintLabel;
    @FXML private TextField captchaAnswerField;
    @FXML private Button captchaRefreshButton;
    @FXML private Button captchaToggleButton;

    @FXML private Button loginButton;
    @FXML private ProgressIndicator loadingIndicator;

    private Timeline lockoutTimeline;
    private String captchaId;
    private String captchaText;
    private boolean captchaPanelOpen = false;

    @FXML
    public void initialize() {
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        initializeCaptchaState();

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
        captchaAnswerField.textProperty().addListener((obs, ov, nv) -> updateLoginButtonState());

        emailField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                if (validateEmailField()) {
                    restorePersistedSecurityState(emailField.getText().trim());
                }
            }
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
        captchaAnswerField.setOnAction(e -> handleLogin());

        updateLoginButtonState();
        restorePersistedSecurityState(null);
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
    private void refreshCaptcha() {
        fetchCaptchaChallenge(true);
    }

    @FXML
    private void toggleCaptchaPanel() {
        setCaptchaPanelOpen(!captchaPanelOpen);
        if (captchaPanelOpen && (captchaId == null || captchaId.isBlank())) {
            fetchCaptchaChallenge(true);
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

        if (captchaRequired) {
            if (captchaId == null || captchaId.isBlank()) {
                setCaptchaPanelOpen(true);
                fetchCaptchaChallenge(true);
                ErrorHandler.showFieldError(globalError, "Chargez puis saisissez le CAPTCHA avant de continuer.");
                return;
            }
            if (captchaAnswerField.getText() == null || captchaAnswerField.getText().trim().isEmpty()) {
                setCaptchaPanelOpen(true);
                ErrorHandler.showFieldError(globalError, "Entrez le texte du CAPTCHA.");
                ErrorHandler.showInlineError(captchaAnswerField, "CAPTCHA requis");
                return;
            }
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
                if (captchaRequired) {
                    payload.put("captchaId", captchaId);
                    payload.put("captchaAnswer", captchaAnswerField.getText().trim());
                }
                return client.sendHybrid(new Request(MessageProtocol.ACTION_LOGIN, payload));
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
                    session.setRole(com.chrionline.model.Session.Role.valueOf(payload.optString("role", "CLIENT")));
                    session.setAdminAccessGranted(false);
                } catch (Exception ignored) {
                }
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
                if (failedLoginAttempts >= 3 || payload.optBoolean("captchaRequired", false)) {
                    captchaRequired = true;
                    showCaptchaIfNeeded();
                    fetchCaptchaChallenge(true);
                }
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
            if (lower.contains("captcha")) {
                captchaRequired = true;
                showCaptchaIfNeeded();
                fetchCaptchaChallenge(true);
                ErrorHandler.showErrorDialog("Verification CAPTCHA",
                    msg.isBlank() ? "Veuillez resoudre le CAPTCHA avant de vous connecter." : msg);
                return;
            }
            if (lower.contains("suspend")) {
                ErrorHandler.showErrorDialog("Compte suspendu", "Compte suspendu. Contactez l'administrateur.");
                return;
            }

            handleFailedLoginAttempt();
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

    @FXML
    private void goToAdminLogin() {
        SceneManager.showAdminLogin();
    }

    @FXML
    private void handleForgotPassword() {
        SceneManager.showForgotPasswordRequest();
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading || lockoutActive);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        captchaRefreshButton.setDisable(loading);
        if (captchaToggleButton != null) {
            captchaToggleButton.setDisable(loading);
        }
    }

    private void updateLoginButtonState() {
        boolean hasEmail = emailField.getText() != null && !emailField.getText().trim().isEmpty();
        String pwd = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        boolean hasPwd = pwd != null && !pwd.isBlank();
        boolean captchaValid = !captchaRequired
            || (captchaId != null && !captchaId.isBlank()
            && captchaAnswerField.getText() != null
            && !captchaAnswerField.getText().trim().isEmpty());
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
                if (captchaRequired) {
                    showCaptchaIfNeeded();
                    if (captchaId == null || captchaId.isBlank()) {
                        fetchCaptchaChallenge(true);
                    }
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
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void initializeCaptchaState() {
        if (captchaRequired) {
            showCaptchaIfNeeded();
            setCaptchaPanelOpen(false);
        } else {
            hideCaptcha();
        }
    }

    private void handleFailedLoginAttempt() {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 12) {
            ipBlocked = true;
            showIpBlockedMessage();
            sendSecurityAlertEmail();
            return;
        }
        if (failedLoginAttempts >= 9) {
            startLockout(3600);
        } else if (failedLoginAttempts >= 6) {
            startLockout(600);
        } else if (failedLoginAttempts >= 3) {
            startLockout(60);
        }
        if (failedLoginAttempts >= 3) {
            captchaRequired = true;
            showCaptchaIfNeeded();
            fetchCaptchaChallenge(true);
        }
    }

    private void showIpBlockedMessage() {
        ErrorHandler.showFieldError(globalError,
            "Trop de tentatives de connexion. Votre acces est bloque pour securite. Un email de notification a ete envoye.");
        loginButton.setDisable(true);
    }

    private void sendSecurityAlertEmail() {
        org.apache.logging.log4j.LogManager.getLogger(LoginController.class)
            .warn("SECURITY ALERT: Multiple failed login attempts detected. IP blocked.");
    }

    private void showCaptchaIfNeeded() {
        captchaBox.setVisible(true);
        captchaBox.setManaged(true);
        setCaptchaPanelOpen(false);
        showCaptchaHint("Cliquez sur le bouton pour ouvrir le panneau de verification.");
    }

    private void hideCaptcha() {
        captchaBox.setVisible(false);
        captchaBox.setManaged(false);
        setCaptchaPanelOpen(false);
        captchaContainer.getChildren().clear();
        showCaptchaHint("");
        captchaId = null;
        captchaText = null;
        captchaAnswerField.clear();
    }

    private void resetSecurityCounters() {
        failedLoginAttempts = 0;
        ipBlocked = false;
        captchaRequired = false;
        lockoutActive = false;
        lockoutSecondsRemaining = 0;
        captchaId = null;
        captchaText = null;
        captchaAnswerField.clear();
        if (lockoutTimeline != null) {
            lockoutTimeline.stop();
            lockoutTimeline = null;
        }
        hideCaptcha();
        ErrorHandler.clearFieldError(globalError);
        updateLoginButtonState();
    }

    private void restorePersistedSecurityState(String email) {
        Task<Response> stateTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                if (email != null && !email.isBlank()) {
                    payload.put("email", email);
                }
                return client.send(new Request(MessageProtocol.ACTION_GET_LOGIN_SECURITY_STATE, payload));
            }
        };

        stateTask.setOnSucceeded(event -> applyPersistedSecurityState(stateTask.getValue()));
        stateTask.setOnFailed(event -> updateLoginButtonState());

        Thread thread = new Thread(stateTask, "login-security-state");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyPersistedSecurityState(Response response) {
        if (response == null || !response.isSuccess()) {
            updateLoginButtonState();
            return;
        }

        JSONObject payload = response.getPayloadAsJsonObject();
        failedLoginAttempts = payload.optInt("failures", failedLoginAttempts);
        captchaRequired = payload.optBoolean("captchaRequired", false);
        ipBlocked = payload.optBoolean("ipBlocked", false);

        if (ipBlocked) {
            int seconds = payload.optInt("ipBlockedSecondsRemaining", 0);
            lockoutActive = false;
            lockoutSecondsRemaining = 0;
            if (lockoutTimeline != null) {
                lockoutTimeline.stop();
                lockoutTimeline = null;
            }
            showCaptchaIfNeeded();
            if (captchaId == null || captchaId.isBlank()) {
                fetchCaptchaChallenge(true);
            }
            ErrorHandler.showFieldError(globalError,
                seconds > 0
                    ? "Trop de tentatives. Votre acces est bloque pour securite pendant encore " + seconds + "s."
                    : "Trop de tentatives de connexion. Votre acces est bloque pour securite.");
            updateLoginButtonState();
            return;
        }

        if (captchaRequired) {
            showCaptchaIfNeeded();
            if (captchaId == null || captchaId.isBlank()) {
                fetchCaptchaChallenge(true);
            }
        } else {
            hideCaptcha();
        }

        if (payload.optBoolean("lockoutActive", false)) {
            startLockout(payload.optInt("lockoutSecondsRemaining", 0));
        } else {
            lockoutActive = false;
            lockoutSecondsRemaining = 0;
            if (lockoutTimeline != null) {
                lockoutTimeline.stop();
                lockoutTimeline = null;
            }
            ErrorHandler.clearFieldError(globalError);
            updateLoginButtonState();
        }
    }

    private void fetchCaptchaChallenge(boolean force) {
        if (!captchaRequired) {
            return;
        }
        if (!force && captchaId != null && !captchaId.isBlank()) {
            return;
        }
        setCaptchaPanelOpen(true);
        showCaptchaHint("Chargement du CAPTCHA...");

        Task<Response> captchaTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_GET_LOGIN_CAPTCHA, new JSONObject()));
            }
        };

        captchaTask.setOnSucceeded(event -> {
            Response response = captchaTask.getValue();
            if (!response.isSuccess()) {
                captchaId = null;
                captchaText = null;
                captchaContainer.getChildren().clear();
                showCaptchaHint("Impossible de charger le CAPTCHA. Reessayez.");
                updateLoginButtonState();
                return;
            }

            JSONObject payload = response.getPayloadAsJsonObject();
            captchaId = payload.optString("captchaId", "");
            captchaText = payload.optString("captchaText", "");
            captchaAnswerField.clear();
            renderCaptcha(captchaText);
            showCaptchaHint("Recopiez le texte de securite puis reconnectez-vous.");
            updateLoginButtonState();
        });

        captchaTask.setOnFailed(event -> {
            captchaId = null;
            captchaText = null;
            captchaContainer.getChildren().clear();
            showCaptchaHint("Impossible de charger le CAPTCHA. Reessayez.");
            updateLoginButtonState();
        });

        Thread thread = new Thread(captchaTask, "login-captcha-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderCaptcha(String text) {
        Canvas canvas = new Canvas(320, 92);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#fff7ed"));
        gc.fillRoundRect(0, 0, 320, 92, 14, 14);
        gc.setStroke(Color.web("#fdba74"));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(1, 1, 318, 90, 14, 14);

        Random random = new Random(text == null ? 0L : text.hashCode());
        for (int i = 0; i < 11; i++) {
            gc.setStroke(Color.rgb(244, 106 + random.nextInt(80), 61 + random.nextInt(120), 0.28));
            gc.setLineWidth(1 + random.nextDouble() * 2.1);
            gc.strokeLine(random.nextInt(320), random.nextInt(92), random.nextInt(320), random.nextInt(92));
        }
        for (int i = 0; i < 40; i++) {
            gc.setFill(Color.rgb(71, 85, 105, 0.12 + (random.nextDouble() * 0.2)));
            gc.fillOval(random.nextInt(320), random.nextInt(92), 2 + random.nextDouble() * 4, 2 + random.nextDouble() * 4);
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Verdana", FontWeight.EXTRA_BOLD, 34));

        char[] chars = text == null ? new char[0] : text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double x = 42 + (i * 45) + random.nextInt(8);
            double y = 46 + random.nextInt(18) - 9;
            double angle = -18 + random.nextInt(37);
            gc.save();
            gc.translate(x, y);
            gc.rotate(angle);
            gc.setFill(i % 2 == 0 ? Color.web("#9a3412") : Color.web("#334155"));
            gc.fillText(String.valueOf(chars[i]), 0, 0);
            gc.restore();
        }

        captchaContainer.getChildren().setAll(canvas);
    }

    private void showCaptchaHint(String message) {
        boolean visible = message != null && !message.isBlank();
        captchaHintLabel.setText(visible ? message : "");
        captchaHintLabel.setVisible(visible);
        captchaHintLabel.setManaged(visible);
    }

    private void setCaptchaPanelOpen(boolean open) {
        captchaPanelOpen = open;
        if (captchaPanel != null) {
            captchaPanel.setVisible(open);
            captchaPanel.setManaged(open);
        }
        if (captchaToggleButton != null) {
            captchaToggleButton.setText(open ? "Hide check" : "Open check");
        }
    }
}
