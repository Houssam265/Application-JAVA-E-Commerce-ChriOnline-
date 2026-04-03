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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import org.json.JSONObject;

public class EmailVerificationController {

    @FXML private Label emailValueLabel;
    @FXML private TextField codeField;
    @FXML private Label codeError;
    @FXML private Label globalError;
    @FXML private Label successMessage;
    @FXML private Button verifyButton;
    @FXML private Button resendButton;
    @FXML private ProgressIndicator loadingIndicator;

    private String email;

    @FXML
    public void initialize() {
        codeField.textProperty().addListener((obs, oldValue, newValue) -> {
            ErrorHandler.clearFieldError(codeError);
            ErrorHandler.clearInlineError(codeField);
            hideMessages();
            updateButtonsState();
        });
        codeField.setOnAction(event -> handleVerify());
        updateButtonsState();
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
        emailValueLabel.setText(this.email.isBlank() ? "-" : this.email);
    }

    @FXML
    private void handleVerify() {
        hideMessages();

        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        if (!validateCode(code)) {
            return;
        }
        if (email == null || email.isBlank()) {
            ErrorHandler.showErrorDialog("Email manquant", "Aucun email n'a ete fourni pour la verification.");
            return;
        }

        setLoading(true);

        Task<Response> verifyTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("code", code);
                return client.send(new Request(MessageProtocol.ACTION_VERIFY_EMAIL, payload));
            }
        };

        verifyTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = verifyTask.getValue();
            if (response.isSuccess()) {
                showSuccess("Email verifie avec succes. Redirection vers la connexion...");
                Task<Void> delay = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(1200);
                        return null;
                    }
                };
                delay.setOnSucceeded(e -> Platform.runLater(SceneManager::showLogin));
                Thread thread = new Thread(delay);
                thread.setDaemon(true);
                thread.start();
                return;
            }
            showGlobalError(response.getMessage());
        });

        verifyTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = verifyTask.getException();
            showGlobalError(cause != null ? String.valueOf(cause.getMessage()) : "Erreur reseau.");
        });

        Thread thread = new Thread(verifyTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleResend() {
        hideMessages();

        if (email == null || email.isBlank()) {
            ErrorHandler.showErrorDialog("Email manquant", "Aucun email n'a ete fourni pour le renvoi.");
            return;
        }

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
            Response response = resendTask.getValue();
            if (response.isSuccess()) {
                showSuccess("Un nouveau code a ete envoye a votre email.");
                return;
            }
            showGlobalError(response.getMessage());
        });

        resendTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = resendTask.getException();
            showGlobalError(cause != null ? String.valueOf(cause.getMessage()) : "Erreur reseau.");
        });

        Thread thread = new Thread(resendTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
    }

    @FXML
    private void goToRegister() {
        SceneManager.showRegister();
    }

    private boolean validateCode(String code) {
        if (code.isBlank()) {
            ErrorHandler.showFieldError(codeError, "Le code est obligatoire");
            ErrorHandler.showInlineError(codeField, "obligatoire");
            return false;
        }
        if (!code.matches("\\d{6}")) {
            ErrorHandler.showFieldError(codeError, "Le code doit contenir 6 chiffres");
            ErrorHandler.showInlineError(codeField, "code invalide");
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        verifyButton.setDisable(loading);
        resendButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        if (!loading) {
            updateButtonsState();
        }
    }

    private void updateButtonsState() {
        boolean ready = codeField.getText() != null && !codeField.getText().trim().isEmpty();
        if (!loadingIndicator.isVisible()) {
            verifyButton.setDisable(!ready);
            resendButton.setDisable(email == null || email.isBlank());
        }
    }

    private void hideMessages() {
        globalError.setVisible(false);
        globalError.setManaged(false);
        successMessage.setVisible(false);
        successMessage.setManaged(false);
    }

    private void showGlobalError(String message) {
        globalError.setText((message == null || message.isBlank()) ? "Verification impossible." : message);
        globalError.setVisible(true);
        globalError.setManaged(true);
    }

    private void showSuccess(String message) {
        successMessage.setText(message);
        successMessage.setVisible(true);
        successMessage.setManaged(true);
    }
}
