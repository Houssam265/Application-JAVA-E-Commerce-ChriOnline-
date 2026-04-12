package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import org.json.JSONObject;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private Label usernameError;
    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label confirmPasswordError;
    @FXML private Label globalError;
    @FXML private Label successMessage;

    @FXML private Button registerButton;
    @FXML private ProgressIndicator loadingIndicator;

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String USERNAME_REGEX = "^[A-Za-z0-9_]{3,30}$";
    private static final int MIN_PASSWORD = 8;
    private static final String PASSWORD_RULES =
            "8+ caracteres, majuscule, minuscule, chiffre, caractere special, sans nom d'utilisateur";

    @FXML
    public void initialize() {
        usernameField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(usernameError);
            ErrorHandler.clearInlineError(usernameField);
            updateRegisterButtonState();
        });
        emailField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(emailError);
            ErrorHandler.clearInlineError(emailField);
            updateRegisterButtonState();
        });
        passwordField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(passwordError);
            ErrorHandler.clearInlineError(passwordField);
            updateRegisterButtonState();
        });
        confirmPasswordField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(confirmPasswordError);
            ErrorHandler.clearInlineError(confirmPasswordField);
            updateRegisterButtonState();
        });

        usernameField.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) validateUsernameField();
        });
        emailField.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) validateEmailField();
        });
        passwordField.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) validatePasswordField();
        });
        confirmPasswordField.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) validateConfirmPasswordField();
        });

        confirmPasswordField.setOnAction(e -> handleRegister());
        updateRegisterButtonState();
    }

    @FXML
    private void handleRegister() {
        ErrorHandler.clearFieldError(usernameError);
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearFieldError(confirmPasswordError);
        ErrorHandler.clearFieldError(globalError);
        hideSuccess();

        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        boolean valid = validateUsernameField() & validateEmailField() & validatePasswordField() & validateConfirmPasswordField();
        if (!valid) {
            return;
        }

        setLoading(true);

        Task<Response> registerTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                try {
                    client.connect();
                } catch (Exception e) {
                    throw new Exception("Impossible de joindre le serveur. Verifiez votre connexion.", e);
                }

                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("email", email);
                payload.put("password", password);
                return client.send(new Request(MessageProtocol.ACTION_REGISTER, payload));
            }
        };

        registerTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = registerTask.getValue();

            if (response.isSuccess()) {
                showSuccess("Compte cree. Un code de verification a ete envoye a votre email.");
                SceneManager.showEmailVerification(email);
                return;
            }

            String msg = response.getMessage() == null ? "" : response.getMessage().trim();
            String lower = msg.toLowerCase();
            if (isUsernameAlreadyUsedMessage(lower)) {
                ErrorHandler.showFieldError(usernameError, "Ce nom d'utilisateur est deja utilise");
                ErrorHandler.showInlineError(usernameField, "Username deja utilise");
            } else if (isEmailAlreadyUsedMessage(lower)) {
                handleExistingEmailDuringRegistration(email);
            } else if (ErrorHandler.isSessionExpiredMessage(msg)) {
                ErrorHandler.handleSessionExpired();
            } else {
                ErrorHandler.showErrorDialog("Inscription echouee",
                        msg.isBlank() ? "Inscription echouee. Veuillez reessayer." : msg);
            }
        });

        registerTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = registerTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur reseau s'est produite.";
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) {
                ErrorHandler.showErrorDialog("Timeout", "La requete a expire. Verifiez votre connexion.");
            } else {
                ErrorHandler.showErrorDialog("Serveur indisponible", "Serveur indisponible. Verifiez votre connexion.");
            }
            if (usernameField.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(usernameField.getScene(), this::handleRegister);
            }
        });

        Thread thread = new Thread(registerTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
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

    private void updateRegisterButtonState() {
        boolean ready = !usernameField.getText().trim().isEmpty()
                && !emailField.getText().trim().isEmpty()
                && !passwordField.getText().isBlank()
                && !confirmPasswordField.getText().isBlank();
        if (!loadingIndicator.isVisible()) {
            registerButton.setDisable(!ready);
        }
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
            ErrorHandler.showFieldError(passwordError, "Mot de passe trop court (minimum 8 caracteres)");
            ErrorHandler.showInlineError(passwordField, "trop court");
            return false;
        }
        if (!isStrongPassword(password)) {
            ErrorHandler.showFieldError(passwordError, "Mot de passe faible: " + PASSWORD_RULES);
            ErrorHandler.showInlineError(passwordField, "mot de passe faible");
            return false;
        }
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (!username.isEmpty() && password.toLowerCase().contains(username.toLowerCase())) {
            ErrorHandler.showFieldError(passwordError, "Le mot de passe ne doit pas contenir le nom d'utilisateur");
            ErrorHandler.showInlineError(passwordField, "contient username");
            return false;
        }
        return true;
    }

    private boolean isStrongPassword(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
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

    private boolean isUsernameAlreadyUsedMessage(String lowerMessage) {
        return "username already taken".equals(lowerMessage)
                || "ce nom d'utilisateur est deja utilise".equals(lowerMessage)
                || "ce nom d'utilisateur est dÃ©jÃ  utilisÃ©".equals(lowerMessage)
                || "ce nom d'utilisateur est déjà utilisé".equals(lowerMessage);
    }

    private boolean isEmailAlreadyUsedMessage(String lowerMessage) {
        return "email already in use".equals(lowerMessage)
                || "cet email est deja utilise".equals(lowerMessage)
                || "cet email est dÃ©jÃ  utilisÃ©".equals(lowerMessage)
                || "cet email est déjà utilisé".equals(lowerMessage);
    }

    private void handleExistingEmailDuringRegistration(String email) {
        setLoading(true);

        Task<Response> resendTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                return client.send(new Request(MessageProtocol.ACTION_RESEND_VERIFICATION_EMAIL, payload));
            }
        };

        resendTask.setOnSucceeded(event -> {
            setLoading(false);
            Response resendResponse = resendTask.getValue();
            if (resendResponse.isSuccess()) {
                showSuccess("Ce compte existe deja mais n'est pas encore verifie. Un nouveau code a ete envoye.");
                SceneManager.showEmailVerification(email);
                return;
            }

            ErrorHandler.showFieldError(emailError, "Cet email est deja utilise");
            ErrorHandler.showInlineError(emailField, "Email deja utilise");
        });

        resendTask.setOnFailed(event -> {
            setLoading(false);
            ErrorHandler.showFieldError(emailError, "Cet email est deja utilise");
            ErrorHandler.showInlineError(emailField, "Email deja utilise");
        });

        Thread thread = new Thread(resendTask);
        thread.setDaemon(true);
        thread.start();
    }
}
