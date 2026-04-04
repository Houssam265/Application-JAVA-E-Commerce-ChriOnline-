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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import org.json.JSONObject;

public class ForgotPasswordRequestController {

    @FXML private TextField emailField;
    @FXML private Label emailError;
    @FXML private Label globalError;
    @FXML private Button sendButton;
    @FXML private ProgressIndicator loadingIndicator;

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    @FXML
    public void initialize() {
        emailField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(emailError);
            ErrorHandler.clearInlineError(emailField);
            updateSendButtonState();
        });

        emailField.setOnAction(e -> handleSendResetEmail());
        updateSendButtonState();
    }

    @FXML
    private void handleSendResetEmail() {
        ErrorHandler.clearFieldError(emailError);
        ErrorHandler.clearFieldError(globalError);

        String email = emailField.getText().trim();

        boolean valid = validateEmailField();
        if (!valid) {
            return;
        }

        setLoading(true);

        Task<Response> forgotTask = new Task<>() {
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
                return client.send(new Request(MessageProtocol.ACTION_FORGOT_PASSWORD, payload));
            }
        };

        forgotTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = forgotTask.getValue();
            if (response.isSuccess()) {
                ErrorHandler.showInfoDialog("Email envoye", "Un email de reinitialisation a ete envoye a " + email + ". Verifiez votre boite de reception et utilisez le code pour reinitialiser votre mot de passe.");
                SceneManager.showResetPassword();
            } else {
                ErrorHandler.showErrorDialog("Erreur", response.getMessage() != null ? response.getMessage() : "Erreur lors de l'envoi de l'email.");
            }
        });

        forgotTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = forgotTask.getException();
            String msg = cause != null ? String.valueOf(cause.getMessage()) : "Une erreur reseau s'est produite.";
            ErrorHandler.showErrorDialog("Serveur indisponible", msg);
        });

        Thread thread = new Thread(forgotTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
    }

    @FXML
    private void goToResetPassword() {
        SceneManager.showResetPassword();
    }

    private void setLoading(boolean loading) {
        sendButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void updateSendButtonState() {
        boolean hasEmail = emailField.getText() != null && !emailField.getText().trim().isEmpty();
        if (!loadingIndicator.isVisible()) {
            sendButton.setDisable(!hasEmail);
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
        return true;
    }
}