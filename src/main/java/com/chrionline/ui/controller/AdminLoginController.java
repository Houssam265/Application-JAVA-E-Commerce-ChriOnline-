package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.security.RSAUtil;
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
import javafx.stage.FileChooser;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;

public class AdminLoginController {

    @FXML private TextField adminUsernameField;
    @FXML private Label adminUsernameError;
    @FXML private Label keyFileLabel;
    @FXML private Label keyError;
    @FXML private Label globalError;
    @FXML private Button loginButton;
    @FXML private ProgressIndicator loadingIndicator;

    private PrivateKey privateKey;

    @FXML
    public void initialize() {
        adminUsernameField.textProperty().addListener((obs, ov, nv) -> {
            ErrorHandler.clearFieldError(adminUsernameError);
            ErrorHandler.clearInlineError(adminUsernameField);
        });
    }

    @FXML
    private void handleSelectKeyFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Private Key (PEM format)");
        File file = fileChooser.showOpenDialog(adminUsernameField.getScene().getWindow());
        if (file != null) {
            try {
                String keyContent = Files.readString(file.toPath()).trim();
                // Clean PEM Headers and whitespaces
                keyContent = keyContent.replaceAll("-----[A-Z ]+-----", "")
                                       .replaceAll("\\s", "");
                
                this.privateKey = RSAUtil.decodePrivateKey(keyContent);
                keyFileLabel.setText(file.getName());
                ErrorHandler.clearFieldError(keyError);
            } catch (Exception e) {
                this.privateKey = null;
                keyFileLabel.setText("Invalid key file");
                ErrorHandler.showFieldError(keyError, "Erreur de chargement: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleLogin() {
        ErrorHandler.clearFieldError(adminUsernameError);
        ErrorHandler.clearFieldError(keyError);
        ErrorHandler.clearFieldError(globalError);

        String adminUsername = adminUsernameField.getText().trim();
        if (adminUsername.isEmpty()) {
            ErrorHandler.showFieldError(adminUsernameError, "Admin Username est requis");
            ErrorHandler.showInlineError(adminUsernameField, "Champs obligatoire");
            return;
        }

        if (privateKey == null) {
            ErrorHandler.showFieldError(keyError, "La clé privée RSA est requise");
            return;
        }

        setLoading(true);

        Task<Response> authTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                try {
                	client.connect();
                } catch (Exception e) {
                	throw new Exception("Impossible de joindre le serveur. Verifiez votre connexion.", e);
                }

                // 1. Démarrer le Challenge
                JSONObject reqObj = new JSONObject();
                reqObj.put("username", adminUsername);
                Response challengeResp = client.send(new Request(MessageProtocol.ACTION_ADMIN_CHALLENGE_REQUEST, reqObj));

                if (!challengeResp.isSuccess()) {
                    return challengeResp;
                }

                String challenge = challengeResp.getPayloadAsJsonObject().getString("challenge");

                // 2. Signer le Challenge localement
                String signature = RSAUtil.sign(challenge, privateKey);

                // 3. Envoyer la signature (Challenge Response) au Serveur
                JSONObject verifyObj = new JSONObject();
                verifyObj.put("username", adminUsername);
                verifyObj.put("signature", signature);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_CHALLENGE_VERIFY, verifyObj));
            }
        };

        authTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = authTask.getValue();

            if (response.isSuccess()) {
                JSONObject payload = response.getPayloadAsJsonObject();
                ClientSession session = ClientSession.getInstance();
                session.setUserId(payload.getInt("adminId"));
                session.setRole(com.chrionline.model.Session.Role.valueOf(payload.getString("role")));
                session.setUsername(adminUsername); 
                
                Platform.runLater(SceneManager::showAdmin);
            } else {
                ErrorHandler.showFieldError(globalError, response.getMessage() != null ? response.getMessage() : "Échec de l'authentification");
            }
        });

        authTask.setOnFailed(event -> {
            setLoading(false);
            Throwable cause = authTask.getException();
            ErrorHandler.showFieldError(globalError, cause != null ? cause.getMessage() : "Erreur réseau");
        });

        Thread t = new Thread(authTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        adminUsernameField.setDisable(loading);
    }
}
