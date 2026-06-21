package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
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

    public enum VerificationPurpose {
        ACCOUNT_EMAIL,
        LOGIN_IP
    }

    @FXML private Label emailValueLabel;
    @FXML private TextField codeField;
    @FXML private Label codeError;
    @FXML private Label globalError;
    @FXML private Label successMessage;
    @FXML private Button verifyButton;
    @FXML private Button resendButton;
    @FXML private ProgressIndicator loadingIndicator;

    private String email;
    private VerificationPurpose purpose = VerificationPurpose.ACCOUNT_EMAIL;

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

    public void configure(String email, VerificationPurpose purpose) {
        this.purpose = purpose == null ? VerificationPurpose.ACCOUNT_EMAIL : purpose;
        setEmail(email);
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
        emailValueLabel.setText(this.email.isBlank() ? "-" : this.email);
        updateButtonsState();
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
                String action = purpose == VerificationPurpose.LOGIN_IP
                        ? MessageProtocol.ACTION_VERIFY_LOGIN_IP
                        : MessageProtocol.ACTION_VERIFY_EMAIL;
                return client.send(new Request(action, payload));
            }
        };

        verifyTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = verifyTask.getValue();
            if (response.isSuccess()) {
                if (purpose == VerificationPurpose.LOGIN_IP) {
                    hydrateSession(response);
                    showSuccess("Connexion verifiee avec succes. Redirection vers l'accueil...");
                } else {
                    showSuccess("Email verifie avec succes. Redirection vers la connexion...");
                }
                Task<Void> delay = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(1200);
                        return null;
                    }
                };
                delay.setOnSucceeded(e -> Platform.runLater(() -> {
                    if (purpose == VerificationPurpose.LOGIN_IP) {
                        SceneManager.showHome();
                    } else {
                        SceneManager.showLogin();
                    }
                }));
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
                String action = purpose == VerificationPurpose.LOGIN_IP
                        ? MessageProtocol.ACTION_RESEND_LOGIN_IP_VERIFICATION
                        : MessageProtocol.ACTION_RESEND_VERIFICATION_EMAIL;
                return client.send(new Request(action, payload));
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

    private void hydrateSession(Response response) {
        try {
            org.json.JSONObject payload = response.getPayloadAsJsonObject();
            ClientSession session = ClientSession.getInstance();
            session.setUserId(payload.has("userId") ? payload.getInt("userId") : null);
            session.setUsername(payload.optString("username", ""));
            session.setEmail(payload.optString("email", ""));
            String role = payload.optString("role", "CLIENT");
            session.setRole(com.chrionline.model.Session.Role.valueOf(role));
            session.setAdminAccessGranted(false);
        } catch (Exception ignored) {
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
