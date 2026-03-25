package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import com.chrionline.ui.notifications.AppNotification;
import com.chrionline.ui.notifications.NotificationCenter;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ProfileController {

    @FXML private Label avatarInitials;
    @FXML private Label displayNameLabel;
    @FXML private Label emailLabel;
    @FXML private Label registeredAtLabel;

    @FXML private TextField nameField;
    @FXML private TextField emailField;

    @FXML private Button editButton;
    @FXML private VBox editBox;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;
    @FXML private Label oldPasswordError;
    @FXML private Label newPasswordError;
    @FXML private Label confirmPasswordError;
    @FXML private Node strengthBar;
    @FXML private Label strengthLabel;
    @FXML private Label globalMessage;

    // Notifications
    @FXML private Button bellButton;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<AppNotification> notificationsList;

    // Navbar active state
    @FXML private Button navProfileBtn;

    private boolean editMode = false;

    @FXML
    public void initialize() {
        hydrateFromSession();
        bindNotifications();
        setActiveNav();

        if (newPasswordField != null) {
            newPasswordField.textProperty().addListener((obs, ov, nv) -> updateStrength(nv));
            newPasswordField.textProperty().addListener((obs, ov, nv) -> {
                ErrorHandler.clearFieldError(newPasswordError);
                ErrorHandler.clearInlineError(newPasswordField);
            });
        }
        if (confirmNewPasswordField != null) {
            confirmNewPasswordField.textProperty().addListener((obs, ov, nv) -> {
                ErrorHandler.clearFieldError(confirmPasswordError);
                ErrorHandler.clearInlineError(confirmNewPasswordField);
            });
        }
        if (nameField != null) {
            nameField.textProperty().addListener((obs, ov, nv) -> {
                ErrorHandler.clearFieldError(globalMessage);
                ErrorHandler.clearInlineError(nameField);
            });
        }
    }

    private void hydrateFromSession() {
        ClientSession s = ClientSession.getInstance();
        String username = s.getUsername() == null ? "" : s.getUsername();
        String email = s.getEmail() == null ? "" : s.getEmail();

        displayNameLabel.setText(username.isBlank() ? "Utilisateur" : username);
        emailLabel.setText(email.isBlank() ? "—" : email);
        nameField.setText(username);
        emailField.setText(email);

        avatarInitials.setText(initials(username.isBlank() ? "U" : username));
        registeredAtLabel.setText("Registered: " + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDateTime.now()));
    }

    private String initials(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isBlank()) return "U";
        String[] parts = s.split("\\s+");
        String a = parts[0].substring(0, 1).toUpperCase(Locale.US);
        String b = parts.length > 1 ? parts[1].substring(0, 1).toUpperCase(Locale.US) : "";
        return (a + b).trim();
    }

    private void bindNotifications() {
        NotificationCenter nc = NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(AppNotification item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    String hhmm = item.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));
                    setText("[" + hhmm + "] " + item.getMessage());
                    setOpacity(item.isRead() ? 0.55 : 1.0);
                }
            });
        }

        nc.getNotifications().addListener((ListChangeListener<AppNotification>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    c.getAddedSubList().forEach(n -> showToast("Notification", n.getMessage()));
                }
            }
            refreshNotificationsMenuList();
        });
        refreshNotificationsMenuList();
    }

    private void refreshNotificationsMenuList() {
        NotificationCenter nc = NotificationCenter.getInstance();
        Platform.runLater(() -> {
            if (notificationsList == null) return;
            notificationsList.getItems().setAll(nc.getNotifications());
            notificationsList.refresh();
        });
    }

    private void updateUnreadBadge(int unread) {
        if (unreadBadge == null) return;
        Platform.runLater(() -> {
            unreadBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            boolean show = unread > 0;
            unreadBadge.setVisible(show);
            unreadBadge.setManaged(show);
        });
    }

    private void setActiveNav() {
        if (navProfileBtn != null && !navProfileBtn.getStyleClass().contains("nav-pill-active")) {
            navProfileBtn.getStyleClass().add("nav-pill-active");
        }
    }

    @FXML
    private void toggleNotifications() {
        boolean open = drawerPanel != null && drawerPanel.isVisible();
        if (open) closeNotifications();
        else openNotifications();
    }

    @FXML
    private void noop() {
        // active page
    }

    private void openNotifications() {
        if (drawerPanel == null || drawerScrim == null) return;
        drawerScrim.setVisible(true);
        drawerScrim.setManaged(true);
        drawerPanel.setVisible(true);
        drawerPanel.setManaged(true);
        drawerPanel.setTranslateX(420);
        new Timeline(new KeyFrame(javafx.util.Duration.millis(220),
                new KeyValue(drawerPanel.translateXProperty(), 0, Interpolator.EASE_OUT))).play();
    }

    @FXML
    private void closeNotifications() {
        if (drawerPanel == null || drawerScrim == null) return;
        Timeline t = new Timeline(new KeyFrame(javafx.util.Duration.millis(180),
                new KeyValue(drawerPanel.translateXProperty(), 420, Interpolator.EASE_IN)));
        t.setOnFinished(e -> {
            drawerPanel.setVisible(false);
            drawerPanel.setManaged(false);
            drawerScrim.setVisible(false);
            drawerScrim.setManaged(false);
        });
        t.play();
    }

    @FXML
    private void markAllRead() {
        NotificationCenter.getInstance().markAllAsRead();
        refreshNotificationsMenuList();
    }

    @FXML
    private void toggleEdit() {
        editMode = !editMode;
        editButton.setText(editMode ? "Cancel" : "Edit");
        editBox.setVisible(editMode);
        editBox.setManaged(editMode);
        if (!editMode) {
            oldPasswordField.clear();
            newPasswordField.clear();
            hideError(oldPasswordError);
            hideError(newPasswordError);
            setGlobalMessage(null, false);
        }
    }

    @FXML
    private void handleSave() {
        hideError(oldPasswordError);
        hideError(newPasswordError);
        hideError(confirmPasswordError);
        setGlobalMessage(null, false);

        String newName = nameField.getText() == null ? "" : nameField.getText().trim();
        String mail = emailField.getText() == null ? "" : emailField.getText().trim();
        if (newName.isBlank()) {
            ErrorHandler.showFieldError(globalMessage, "Le nom ne peut pas être vide");
            ErrorHandler.showInlineError(nameField, "nom vide");
            return;
        }

        Task<Response> updateProfile = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("username", newName);
                payload.put("email", mail);
                return client.send(new Request(MessageProtocol.ACTION_UPDATE_PROFILE, payload, client.getSessionToken()));
            }
        };

        updateProfile.setOnSucceeded(e -> {
            Response r = updateProfile.getValue();
            if (!r.isSuccess()) {
                if (ErrorHandler.isSessionExpiredMessage(r.getMessage())) {
                    setGlobalMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                String msg = r.getMessage() == null ? "" : r.getMessage();
                setGlobalMessage(null, false);
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Mise à jour impossible." : msg);
                return;
            }
            ClientSession s = ClientSession.getInstance();
            JSONObject p = r.getPayloadAsJsonObject();
            s.setUsername(p.optString("username", s.getUsername()));
            s.setEmail(p.optString("email", s.getEmail()));
            hydrateFromSession();

            // Password change if fields provided
            String oldPwd = oldPasswordField.getText() == null ? "" : oldPasswordField.getText();
            String newPwd = newPasswordField.getText() == null ? "" : newPasswordField.getText();
            String confirmPwd = confirmNewPasswordField == null || confirmNewPasswordField.getText() == null ? "" : confirmNewPasswordField.getText();
            if (!oldPwd.isBlank() || !newPwd.isBlank()) {
                if (oldPwd.isBlank()) {
                    showError(oldPasswordError, "Ancien mot de passe incorrect");
                    ErrorHandler.showInlineError(oldPasswordField, "old pwd");
                    return;
                }
                if (!isStrongEnough(newPwd)) {
                    showError(newPasswordError, "Mot de passe trop court (minimum 8 caractères)");
                    ErrorHandler.showInlineError(newPasswordField, "new pwd");
                    return;
                }
                if (!newPwd.equals(confirmPwd)) {
                    showError(confirmPasswordError, "Les mots de passe ne correspondent pas");
                    ErrorHandler.showInlineError(confirmNewPasswordField, "confirm mismatch");
                    return;
                }
                changePassword(oldPwd, newPwd);
            } else {
                setGlobalMessage("Profil mis à jour.", false);
                toggleEdit();
            }
        });
        updateProfile.setOnFailed(e -> {
            setGlobalMessage(null, false);
            if (editButton != null && editButton.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(editButton.getScene(), this::handleSave);
            }
        });
        runTask(updateProfile);
    }

    private void changePassword(String oldPwd, String newPwd) {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("old_password", oldPwd);
                payload.put("new_password", newPwd);
                return client.send(new Request(MessageProtocol.ACTION_CHANGE_PASSWORD, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setGlobalMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                if (msg.toLowerCase().contains("ancien") || msg.toLowerCase().contains("old")) {
                    showError(oldPasswordError, "Ancien mot de passe incorrect");
                    ErrorHandler.showInlineError(oldPasswordField, "Ancien mot de passe incorrect");
                    return;
                }
                setGlobalMessage(null, false);
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Changement mot de passe impossible." : msg);
                return;
            }
            setGlobalMessage("Profil + mot de passe mis à jour.", false);
            toggleEdit();
        });
        t.setOnFailed(e -> {
            setGlobalMessage(null, false);
            if (editButton != null && editButton.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(editButton.getScene(), () -> changePassword(oldPwd, newPwd));
            }
        });
        runTask(t);
    }

    private void updateStrength(String pwd) {
        if (strengthLabel == null || strengthBar == null) return;
        int score = strengthScore(pwd);
        String label;
        String color;
        if (score <= 1) { label = "Weak"; color = "rgba(239,68,68,0.75)"; }
        else if (score == 2) { label = "Medium"; color = "rgba(245,158,11,0.75)"; }
        else { label = "Strong"; color = "rgba(34,197,94,0.75)"; }
        strengthLabel.setText(label);
        strengthBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999px;");
    }

    private int strengthScore(String pwd) {
        if (pwd == null) return 0;
        boolean hasLetter = pwd.chars().anyMatch(Character::isLetter);
        boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);
        int score = 0;
        if (pwd.length() >= 8) score++;
        if (hasLetter) score++;
        if (hasDigit) score++;
        return score;
    }

    private boolean isStrongEnough(String pwd) {
        if (pwd == null || pwd.length() < 8) return false;
        boolean hasLetter = pwd.chars().anyMatch(Character::isLetter);
        boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    private void setGlobalMessage(String msg, boolean error) {
        if (globalMessage == null) return;
        if (msg == null || msg.isBlank()) {
            globalMessage.setVisible(false);
            globalMessage.setManaged(false);
            globalMessage.setText("");
            return;
        }
        globalMessage.getStyleClass().setAll(error ? "global-error" : "success-label");
        globalMessage.setText(msg);
        globalMessage.setVisible(true);
        globalMessage.setManaged(true);
    }

    private void showError(Label label, String msg) {
        ErrorHandler.showFieldError(label, msg);
    }

    private void hideError(Label label) {
        ErrorHandler.clearFieldError(label);
    }

    @FXML
    private void handleLogout() {
        setGlobalMessage(null, false);

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_LOGOUT, new JSONObject(), client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            // Fade transition then redirect
            fadeOutThen(() -> {
                Client.getInstance().disconnect();
                ClientSession.getInstance().clear();
                SceneManager.showLogin();
            });
        });
        t.setOnFailed(e -> {
            setGlobalMessage(null, false);
            if (editButton != null && editButton.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(editButton.getScene(), this::handleLogout);
            }
        });
        runTask(t);
    }

    @FXML private void goHome() { SceneManager.showHome(); }
    @FXML private void goCart() { SceneManager.showCart(); }
    @FXML private void goOrders() { SceneManager.showOrderHistory(); }

    private void fadeOutThen(Runnable after) {
        // Fade the whole screen if possible
        Node root = editButton != null ? editButton.getScene().getRoot() : null;
        if (root == null) {
            Platform.runLater(after);
            return;
        }
        FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(220), root);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(ev -> Platform.runLater(after));
        ft.play();
    }

    private void showToast(String title, String body) {
        if (toastLayer == null) return;
        VBox card = new VBox(6);
        card.getStyleClass().add("toast-card");
        Label t = new Label(title);
        t.getStyleClass().add("toast-title");
        Label b = new Label(body);
        b.getStyleClass().add("toast-body");
        b.setWrapText(true);
        card.getChildren().addAll(t, b);

        card.setOpacity(0);
        card.setTranslateX(40);
        toastLayer.getChildren().add(0, card);

        Timeline in = new Timeline(
                new KeyFrame(javafx.util.Duration.millis(220),
                        new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(card.translateXProperty(), 0, Interpolator.EASE_OUT))
        );
        PauseTransition stay = new PauseTransition(javafx.util.Duration.seconds(4));
        Timeline out = new Timeline(
                new KeyFrame(javafx.util.Duration.millis(200),
                        new KeyValue(card.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(card.translateXProperty(), 40, Interpolator.EASE_IN))
        );
        out.setOnFinished(e -> toastLayer.getChildren().remove(card));
        new SequentialTransition(in, stay, out).play();
    }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}

