package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import org.json.JSONObject;
import org.kordamp.ikonli.javafx.FontIcon;

public class ResetPasswordController {

    @FXML private TextField tokenField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private Label tokenError;
    @FXML private Label passwordError;
    @FXML private Label confirmPasswordError;
    @FXML private Label globalError;

    @FXML private Button resetButton;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private TextField passwordTextField;
    @FXML private TextField confirmPasswordTextField;
    @FXML private Button togglePasswordButton;
    @FXML private Button toggleConfirmPasswordButton;
    @FXML private FontIcon togglePasswordIcon;
    @FXML private FontIcon toggleConfirmPasswordIcon;

    private static final int MIN_PASSWORD = 8;

    @FXML
    public void initialize() {
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        confirmPasswordTextField.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        tokenField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(tokenError);
            ErrorHandler.clearInlineError(tokenField);
            updateResetButtonState();
        });
        passwordField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(passwordError);
            ErrorHandler.clearInlineError(passwordField);
            updateResetButtonState();
        });
        confirmPasswordField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(confirmPasswordError);
            ErrorHandler.clearInlineError(confirmPasswordField);
            updateResetButtonState();
        });
        passwordTextField.textProperty().addListener((obs, ov, nv) -> updateResetButtonState());
        confirmPasswordTextField.textProperty().addListener((obs, ov, nv) -> updateResetButtonState());

        passwordField.setOnAction(e -> handleResetPassword());
        confirmPasswordField.setOnAction(e -> handleResetPassword());
        tokenField.setOnAction(e -> handleResetPassword());
        updateResetButtonState();
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
    private void toggleConfirmPasswordVisibility() {
        if (confirmPasswordField.isVisible()) {
            confirmPasswordField.setVisible(false);
            confirmPasswordTextField.setVisible(true);
            toggleConfirmPasswordIcon.setIconLiteral("fas-eye");
        } else {
            confirmPasswordField.setVisible(true);
            confirmPasswordTextField.setVisible(false);
            toggleConfirmPasswordIcon.setIconLiteral("fas-eye-slash");
        }
    }

    @FXML
    private void handleResetPassword() {
        ErrorHandler.clearFieldError(tokenError);
        ErrorHandler.clearFieldError(passwordError);
        ErrorHandler.clearFieldError(confirmPasswordError);
        ErrorHandler.clearFieldError(globalError);

        String token = tokenField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        String confirmPassword = confirmPasswordField.isVisible() ? confirmPasswordField.getText() : confirmPasswordTextField.getText();

        boolean valid = validateTokenField() & validatePasswordField(password) & validateConfirmPasswordField(password, confirmPassword);
        if (!valid) {
            return;
        }

        setLoading(true);

        Task<Response> resetTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                try {
                    client.connect();
                } catch (Exception e) {
                    throw new Exception("Impossible de joindre le serveur. Verifiez votre connexion.", e);
                }

                JSONObject payload = new JSONObject();
                payload.put("token", token);
                payload.put("newPassword", password);
                return client.send(new Request(MessageProtocol.ACTION_RESET_PASSWORD, payload));
            }
        };

        resetTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = resetTask.getValue();
            if (response.isSuccess()) {
                ErrorHandler.showInfoDialog("Password Reset", "Your password has been successfully reset. You can now sign in with your new password.");
                Platform.runLater(SceneManager::showLogin);
            } else {
                ErrorHandler.showErrorDialog("Reset Failed", response.getMessage() != null ? response.getMessage() : "Failed to reset password.");
            }
        });

        resetTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = resetTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur reseau s'est produite.";
            ErrorHandler.showErrorDialog("Serveur indisponible", msg);
        });

        Thread thread = new Thread(resetTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
    }

    private void setLoading(boolean loading) {
        resetButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void updateResetButtonState() {
        boolean hasToken = tokenField.getText() != null && !tokenField.getText().trim().isEmpty();
        String pwd = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();
        boolean hasPwd = pwd != null && !pwd.isBlank();
        String confirmPwd = confirmPasswordField.isVisible() ? confirmPasswordField.getText() : confirmPasswordTextField.getText();
        boolean hasConfirmPwd = confirmPwd != null && !confirmPwd.isBlank();
        if (!loadingIndicator.isVisible()) {
            resetButton.setDisable(!(hasToken && hasPwd && hasConfirmPwd));
        }
    }

    private boolean validateTokenField() {
        String token = tokenField.getText() == null ? "" : tokenField.getText().trim();
        if (token.isEmpty()) {
            ErrorHandler.showFieldError(tokenError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(tokenField, "Ce champ est obligatoire");
            return false;
        }
        if (!token.matches("\\d{6}")) {
            ErrorHandler.showFieldError(tokenError, "Le code doit contenir exactement 6 chiffres");
            ErrorHandler.showInlineError(tokenField, "Code invalide");
            return false;
        }
        ErrorHandler.clearFieldError(tokenError);
        return true;
    }

    private boolean validatePasswordField(String password) {
        if (password == null || password.isBlank()) {
            ErrorHandler.showFieldError(passwordError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(passwordField, "Ce champ est obligatoire");
            return false;
        }
        if (password.length() < MIN_PASSWORD) {
            ErrorHandler.showFieldError(passwordError, "Le mot de passe doit contenir au moins " + MIN_PASSWORD + " caracteres");
            ErrorHandler.showInlineError(passwordField, "Trop court");
            return false;
        }
        ErrorHandler.clearFieldError(passwordError);
        return true;
    }

    private boolean validateConfirmPasswordField(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isBlank()) {
            ErrorHandler.showFieldError(confirmPasswordError, "Ce champ est obligatoire");
            ErrorHandler.showInlineError(confirmPasswordField, "Ce champ est obligatoire");
            return false;
        }
        if (!confirmPassword.equals(password)) {
            ErrorHandler.showFieldError(confirmPasswordError, "Les mots de passe ne correspondent pas");
            ErrorHandler.showInlineError(confirmPasswordField, "Ne correspondent pas");
            return false;
        }
        ErrorHandler.clearFieldError(confirmPasswordError);
        return true;
    }
}