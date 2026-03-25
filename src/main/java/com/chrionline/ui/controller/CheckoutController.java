package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.SceneManager;
import com.chrionline.ui.invoice.InvoicePdfGenerator;
import com.chrionline.ui.notifications.AppNotification;
import com.chrionline.ui.notifications.NotificationCenter;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckoutController {

    @FXML private Label orderIdLabel;
    @FXML private ListView<String> itemsList;
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label totalLabel;

    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private PasswordField cvvField;
    @FXML private Label cardNumberError;
    @FXML private Label cardBrandLabel;
    @FXML private Label expiryError;
    @FXML private Label cvvError;
    @FXML private Button payButton;
    @FXML private Label globalMessage;
    @FXML private StackPane cardBrandContainer;
    @FXML private Canvas cardBrandCanvas;

    // Notifications UI (KAN-31)
    @FXML private Button bellButton;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<AppNotification> notificationsList;

    // Navbar buttons (active state)
    @FXML private Button navCartBtn;

    // Result overlay
    @FXML private StackPane resultOverlay;
    @FXML private VBox resultCard;
    @FXML private org.kordamp.ikonli.javafx.FontIcon resultIcon;
    @FXML private Label resultTitle;
    @FXML private Label resultBody;
    @FXML private Button invoiceButton;

    private String orderId;
    private double subtotal;
    private final double taxRate = 0.10;
    private boolean lastPaymentSuccess = false;
    private CardBrand currentCardBrand = CardBrand.GENERIC;

    @FXML
    public void initialize() {
        orderId = ClientSession.getInstance().getCurrentOrderId();
        if (orderIdLabel != null) {
            orderIdLabel.setText(orderId == null || orderId.isBlank() ? "Order —" : ("Order · " + orderId));
        }

        bindNotifications();
        setActiveNav();
        renderCardBrand(CardBrand.GENERIC, false);
        setupPaymentFormatters();
        loadOrderSummaryFromServer();
    }

    private void setActiveNav() {
        if (navCartBtn != null && !navCartBtn.getStyleClass().contains("nav-pill-active")) {
            navCartBtn.getStyleClass().add("nav-pill-active");
        }
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

    private void setupPaymentFormatters() {
        cardNumberField.textProperty().addListener((obs, oldV, newV) -> {
            String digits = newV.replaceAll("\\D+", "");
            if (digits.length() > 16) digits = digits.substring(0, 16);
            String formatted = digits.replaceAll("(.{4})", "$1 ").trim();
            if (!formatted.equals(newV)) {
                int caret = Math.min(formatted.length(), cardNumberField.getCaretPosition());
                cardNumberField.setText(formatted);
                cardNumberField.positionCaret(caret);
            }
            updateCardBrandUI();
            validateCard(false);
        });

        expiryField.textProperty().addListener((obs, oldV, newV) -> {
            String digits = newV.replaceAll("\\D+", "");
            if (digits.length() > 4) digits = digits.substring(0, 4);
            String formatted = digits;
            if (digits.length() >= 3) formatted = digits.substring(0, 2) + "/" + digits.substring(2);
            if (!formatted.equals(newV)) {
                int caret = Math.min(formatted.length(), expiryField.getCaretPosition());
                expiryField.setText(formatted);
                expiryField.positionCaret(caret);
            }
            validateExpiry(false);
        });

        cvvField.textProperty().addListener((obs, oldV, newV) -> {
            String digits = newV.replaceAll("\\D+", "");
            if (!digits.equals(newV)) cvvField.setText(digits);
            if (digits.length() > 4) cvvField.setText(digits.substring(0, 4));
            validateCvv(false);
        });
    }

    @FXML
    private void handleCardNumberKeyTyped() {
        updateCardBrandUI();
    }

    private void updateCardBrandUI() {
        String digits = cardNumberField != null ? cardNumberField.getText().replaceAll("\\D+", "") : "";
        CardBrand brand = detectCardBrand(digits);
        if (brand != currentCardBrand) {
            renderCardBrand(brand, true);
            currentCardBrand = brand;
        } else if (cardBrandLabel != null) {
            cardBrandLabel.setText(brand.displayName);
        }
    }

    private CardBrand detectCardBrand(String digits) {
        if (digits == null || digits.isBlank()) return CardBrand.GENERIC;
        if (digits.startsWith("4")) return CardBrand.VISA;
        if (digits.length() >= 2) {
            int p2 = parsePrefix(digits, 2);
            if (p2 >= 34 && p2 <= 37 && (p2 == 34 || p2 == 37)) return CardBrand.AMEX;
            if (p2 >= 51 && p2 <= 55) return CardBrand.MASTERCARD;
            if (p2 == 65) return CardBrand.DISCOVER;
        }
        if (digits.length() >= 4) {
            int p4 = parsePrefix(digits, 4);
            if (p4 == 6011) return CardBrand.DISCOVER;
            int p4mc = parsePrefix(digits, 4);
            if (p4mc >= 2221 && p4mc <= 2720) return CardBrand.MASTERCARD;
        }
        return CardBrand.GENERIC;
    }

    private int parsePrefix(String digits, int len) {
        if (digits == null || digits.length() < len) return -1;
        try {
            return Integer.parseInt(digits.substring(0, len));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void renderCardBrand(CardBrand brand, boolean animate) {
        if (cardBrandCanvas == null) return;
        GraphicsContext g = cardBrandCanvas.getGraphicsContext2D();
        double w = cardBrandCanvas.getWidth();
        double h = cardBrandCanvas.getHeight();
        g.clearRect(0, 0, w, h);

        // Base rounded badge
        g.setFill(Color.WHITE);
        g.fillRoundRect(0.5, 0.5, w - 1, h - 1, 8, 8);
        g.setStroke(Color.web("#cbd5e1"));
        g.setLineWidth(1);
        g.strokeRoundRect(0.5, 0.5, w - 1, h - 1, 8, 8);

        switch (brand) {
            case VISA -> {
                g.setFill(Color.web("#1d4ed8"));
                g.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, FontPosture.ITALIC, 10));
                g.fillText("VISA", 8, 16);
            }
            case MASTERCARD -> {
                g.setFill(Color.web("#ef4444"));
                g.fillOval(7, 5, 14, 14);
                g.setFill(Color.web("#f97316"));
                g.fillOval(17, 5, 14, 14);
                g.setFill(Color.WHITE);
                g.setFont(Font.font("Arial", FontWeight.BOLD, 8));
                g.fillText("MC", 13, 16);
            }
            case AMEX -> {
                g.setFill(Color.web("#2563eb"));
                g.fillRoundRect(8, 4, 24, 17, 4, 4);
                g.setFill(Color.WHITE);
                g.setFont(Font.font("Arial", FontWeight.BOLD, 7.2));
                g.fillText("AMEX", 10.5, 16);
            }
            case DISCOVER -> {
                g.setFill(Color.web("#f97316"));
                g.fillOval(8, 4, 24, 17);
                g.setFill(Color.WHITE);
                g.setFont(Font.font("Arial", FontWeight.BOLD, 8));
                g.fillText("DISC", 10.5, 16);
            }
            case GENERIC -> {
                g.setFill(Color.web("#94a3b8"));
                g.fillRoundRect(8, 5, 24, 15, 4, 4);
                g.setFill(Color.web("#e2e8f0"));
                g.fillRect(10, 8, 20, 2.5);
                g.setFill(Color.web("#cbd5e1"));
                g.fillRoundRect(11, 12, 6, 4, 1.5, 1.5);
            }
        }

        if (cardBrandLabel != null) {
            cardBrandLabel.setText(brand.displayName);
        }

        if (animate && cardBrandContainer != null) {
            cardBrandContainer.setOpacity(0.45);
            cardBrandContainer.setScaleX(0.9);
            cardBrandContainer.setScaleY(0.9);
            FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(170), cardBrandContainer);
            ft.setToValue(1.0);
            ScaleTransition st = new ScaleTransition(javafx.util.Duration.millis(170), cardBrandContainer);
            st.setToX(1.0);
            st.setToY(1.0);
            new ParallelTransition(ft, st).play();
        }
    }

    private enum CardBrand {
        VISA("Visa Classic"),
        MASTERCARD("Mastercard"),
        AMEX("American Express"),
        DISCOVER("Discover"),
        GENERIC("Carte bancaire");

        final String displayName;

        CardBrand(String displayName) {
            this.displayName = displayName;
        }
    }

    private void loadOrderSummaryFromServer() {
        if (orderId == null || orderId.isBlank()) {
            setGlobalMessage("Aucune commande à payer. Retourne au panier.", true);
            if (payButton != null) payButton.setDisable(true);
            return;
        }

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("order_id", orderId);
                return client.send(new Request(MessageProtocol.ACTION_GET_ORDER_DETAILS, payload, client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setGlobalMessage(r.getMessage().isBlank() ? "Impossible de charger la commande." : r.getMessage(), true);
                return;
            }
            try {
                JSONObject payload = r.getPayloadAsJsonObject();
                JSONObject order = payload.optJSONObject("order");
                if (order == null) order = payload;

                double total = order.optDouble("totalAmount", order.optDouble("total_amount", 0.0));
                subtotal = total > 0 ? (total / (1.0 + taxRate)) : 0.0;
                double tax = total - subtotal;
                subtotalLabel.setText(String.format(Locale.US, "%.2f Dhs", subtotal));
                taxLabel.setText(String.format(Locale.US, "%.2f Dhs", tax));
                totalLabel.setText(String.format(Locale.US, "%.2f Dhs", total));

                List<String> rows = new ArrayList<>();
                Object itemsObj = order.opt("items");
                if (itemsObj instanceof JSONArray itemsArr) {
                    for (int i = 0; i < itemsArr.length(); i++) {
                        JSONObject it = itemsArr.optJSONObject(i);
                        if (it == null) continue;
                        int pid = it.optInt("productId", it.optInt("product_id", 0));
                        int qty = it.optInt("quantity", 0);
                        double unit = it.optDouble("unitPrice", it.optDouble("unit_price", 0.0));
                        rows.add("Produit #" + pid + "  ×" + qty + "  ·  " + String.format(Locale.US, "%.2f Dhs", unit));
                    }
                }
                if (rows.isEmpty()) rows.add("Commande prête · Paiement requis");
                itemsList.getItems().setAll(rows);
            } catch (Exception ex) {
                itemsList.getItems().setAll("Commande prête · Paiement requis");
            }
        });
        t.setOnFailed(e -> setGlobalMessage("Erreur réseau lors du chargement de la commande.", true));
        runTask(t);
    }

    @FXML
    private void handlePay() {
        hideError(cardNumberError);
        hideError(expiryError);
        hideError(cvvError);
        setGlobalMessage(null, false);

        boolean ok = validateCard(true) & validateExpiry(true) & validateCvv(true);
        if (!ok) return;
        if (orderId == null || orderId.isBlank()) {
            setGlobalMessage("OrderId manquant.", true);
            return;
        }

        payButton.setDisable(true);

        String cardNumber = cardNumberField.getText().trim();
        String expiry = expiryField.getText().trim();
        String cvv = cvvField.getText().trim();

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("order_id", orderId);
                payload.put("card_number", cardNumber);
                payload.put("expiry", expiry);
                payload.put("cvv", cvv);
                return client.send(new Request(MessageProtocol.ACTION_PAYMENT, payload, client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            payButton.setDisable(false);
            Response r = t.getValue();
            if (r.isSuccess()) {
                showResultOverlay(true, "Paiement accepté", "Merci ! Votre paiement a été validé et la commande a été mise à jour.");
            } else {
                showResultOverlay(false, "Paiement refusé", r.getMessage().isBlank() ? "Paiement refusé." : r.getMessage());
            }
        });
        t.setOnFailed(e -> {
            payButton.setDisable(false);
            showResultOverlay(false, "Erreur réseau", "Impossible de contacter le serveur.");
        });
        runTask(t);
    }

    private boolean validateCard(boolean show) {
        String digits = cardNumberField.getText().replaceAll("\\D+", "");
        boolean ok = digits.length() == 16;
        if (!ok && show) showError(cardNumberError, "Le numéro doit contenir 16 chiffres.");
        if (ok) hideError(cardNumberError);
        return ok;
    }

    private boolean validateExpiry(boolean show) {
        String v = expiryField.getText().trim();
        boolean ok = v.matches("^(0[1-9]|1[0-2])\\/\\d{2}$");
        if (!ok && show) showError(expiryError, "Format invalide. Exemple: 08/27");
        if (ok) hideError(expiryError);
        return ok;
    }

    private boolean validateCvv(boolean show) {
        String v = cvvField.getText().trim();
        boolean ok = v.matches("^\\d{3,4}$");
        if (!ok && show) showError(cvvError, "CVV invalide (3-4 chiffres).");
        if (ok) hideError(cvvError);
        return ok;
    }

    private void showError(Label label, String msg) {
        if (label == null) return;
        label.setText(msg);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideError(Label label) {
        if (label == null) return;
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
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

    @FXML
    private void toggleNotifications() {
        boolean open = drawerPanel != null && drawerPanel.isVisible();
        if (open) closeNotifications();
        else openNotifications();
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
        card.setMaxWidth(320);

        card.setOpacity(0);
        card.setTranslateX(56);
        toastLayer.getChildren().add(0, card);
        Timeline in = new Timeline(new KeyFrame(javafx.util.Duration.millis(220),
                new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT),
                new KeyValue(card.translateXProperty(), 0, Interpolator.EASE_OUT)));
        PauseTransition stay = new PauseTransition(javafx.util.Duration.seconds(4));
        Timeline out = new Timeline(new KeyFrame(javafx.util.Duration.millis(220),
                new KeyValue(card.opacityProperty(), 0, Interpolator.EASE_IN),
                new KeyValue(card.translateXProperty(), 56, Interpolator.EASE_IN)));
        out.setOnFinished(e -> toastLayer.getChildren().remove(card));
        new SequentialTransition(in, stay, out).play();
    }

    private void showResultOverlay(boolean success, String title, String body) {
        if (resultOverlay == null || resultCard == null) return;
        lastPaymentSuccess = success;
        resultTitle.setText(title);
        resultBody.setText(body == null ? "" : body);

        if (success) {
            resultIcon.setIconLiteral("fas-check-circle");
            resultIcon.setIconColor(javafx.scene.paint.Color.web("#22c55e"));
        } else {
            resultIcon.setIconLiteral("fas-times-circle");
            resultIcon.setIconColor(javafx.scene.paint.Color.web("#ef4444"));
        }
        if (invoiceButton != null) {
            invoiceButton.setVisible(success);
            invoiceButton.setManaged(success);
        }

        resultOverlay.setOpacity(0);
        resultOverlay.setVisible(true);
        resultOverlay.setManaged(true);

        Timeline fadeIn = new Timeline(new KeyFrame(javafx.util.Duration.millis(200),
                new KeyValue(resultOverlay.opacityProperty(), 1, Interpolator.EASE_OUT)));
        fadeIn.play();

        if (!success) {
            // Shake effect on failure
            TranslateTransition shake = new TranslateTransition(javafx.util.Duration.millis(60), resultCard);
            shake.setFromX(-8);
            shake.setToX(8);
            shake.setAutoReverse(true);
            shake.setCycleCount(6);
            shake.play();
        } else {
            // Simple "confetti style" particles (lightweight): animated labels
            for (int i = 0; i < 18; i++) {
                Label p = new Label("•");
                p.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 18px;");
                p.setTranslateX((Math.random() - 0.5) * 240);
                p.setTranslateY(40 + (Math.random() * 30));
                resultOverlay.getChildren().add(p);
                TranslateTransition tt = new TranslateTransition(javafx.util.Duration.millis(900), p);
                tt.setFromY(p.getTranslateY());
                tt.setToY(p.getTranslateY() - 180 - Math.random() * 120);
                FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(900), p);
                ft.setFromValue(1);
                ft.setToValue(0);
                ParallelTransition pt = new ParallelTransition(tt, ft);
                pt.setOnFinished(e -> resultOverlay.getChildren().remove(p));
                pt.play();
            }
        }
    }

    @FXML
    private void closeResultOverlay() {
        if (resultOverlay == null) return;
        Timeline fadeOut = new Timeline(new KeyFrame(javafx.util.Duration.millis(180),
                new KeyValue(resultOverlay.opacityProperty(), 0, Interpolator.EASE_IN)));
        fadeOut.setOnFinished(e -> {
            resultOverlay.setVisible(false);
            resultOverlay.setManaged(false);
            if (lastPaymentSuccess) {
                SceneManager.showOrderHistory();
            }
        });
        fadeOut.play();
    }

    @FXML
    private void downloadInvoice() {
        if (!lastPaymentSuccess) {
            setGlobalMessage("La facture est disponible après un paiement réussi.", true);
            return;
        }
        if (orderId == null || orderId.isBlank()) {
            setGlobalMessage("OrderId manquant.", true);
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer la facture PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("facture-" + orderId + ".pdf");
        File out = fc.showSaveDialog(payButton != null && payButton.getScene() != null ? payButton.getScene().getWindow() : null);
        if (out == null) return;

        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject p = new JSONObject();
                p.put("order_id", orderId);
                Response r = client.send(new Request(MessageProtocol.ACTION_GET_ORDER_DETAILS, p, client.getSessionToken()));
                if (!r.isSuccess()) {
                    throw new IllegalStateException(r.getMessage().isBlank() ? "Impossible de charger le détail commande." : r.getMessage());
                }

                JSONObject payload = r.getPayloadAsJsonObject();
                JSONObject order = payload.optJSONObject("order");
                if (order == null) order = payload;

                List<InvoicePdfGenerator.InvoiceLine> lines = new ArrayList<>();
                Object itemsObj = order.opt("items");
                if (itemsObj instanceof JSONArray itemsArr) {
                    for (int i = 0; i < itemsArr.length(); i++) {
                        JSONObject it = itemsArr.optJSONObject(i);
                        if (it == null) continue;
                        int pid = it.optInt("productId", it.optInt("product_id", 0));
                        int qty = it.optInt("quantity", 0);
                        double unit = it.optDouble("unitPrice", it.optDouble("unit_price", 0.0));
                        lines.add(new InvoicePdfGenerator.InvoiceLine("Produit #" + pid, qty, unit));
                    }
                }

                double total = order.optDouble("totalAmount", order.optDouble("total_amount", 0.0));
                double sub = total > 0 ? (total / (1.0 + taxRate)) : 0.0;
                double tax = total - sub;

                InvoicePdfGenerator.generate(
                        out,
                        orderId,
                        ClientSession.getInstance().getUsername(),
                        ClientSession.getInstance().getEmail(),
                        LocalDateTime.now(),
                        lines,
                        tax,
                        total
                );
                return null;
            }
        };

        t.setOnSucceeded(e -> setGlobalMessage("Facture enregistrée: " + out.getName(), false));
        t.setOnFailed(e -> setGlobalMessage("Erreur facture: " + (t.getException() != null ? t.getException().getMessage() : "inconnue"), true));
        runTask(t);
    }

    @FXML private void goHome() { SceneManager.showHome(); }
    @FXML private void goCart() { SceneManager.showCart(); }
    @FXML private void goProfile() { SceneManager.showProfile(); }
    @FXML private void goOrders() { SceneManager.showOrderHistory(); }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}

