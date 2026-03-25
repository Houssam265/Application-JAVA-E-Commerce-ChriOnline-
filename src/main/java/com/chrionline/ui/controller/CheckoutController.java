package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.YearMonth;

/**
 * Checkout / Payment controller (KAN-7 client side).
 *
 * Flow:
 *   1. On initialize → GET_CART to display order summary
 *   2. handlePay → client-side card validation → PLACE_ORDER → PAYMENT
 *   3. On success → navigate to Order History
 */
public class CheckoutController {

    // ── Order summary (left column) ──────────────────────────────────────────
    @FXML private VBox summaryContainer;
    @FXML private Label subtotalLabel;
    @FXML private Label totalLabel;

    // ── Payment form (right column) ──────────────────────────────────────────
    @FXML private TextField cardHolderField;
    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private TextField cvvField;

    @FXML private Button payButton;
    @FXML private Label statusLabel;

    /**
     * After a successful PLACE_ORDER, the cart is cleared on the server.
     * If PAYMENT then fails (e.g. Luhn check), the user must be able to retry
     * without calling PLACE_ORDER again (which would fail with "cart is empty").
     * This field stores the orderId so retries go straight to PAYMENT.
     */
    private String pendingOrderId = null;

    @FXML
    public void initialize() {
        hideStatus();
        loadCartSummary();
    }

    // ── Cart summary loading ─────────────────────────────────────────────────

    private void loadCartSummary() {
        Task<Response> t = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(
                        MessageProtocol.ACTION_GET_CART, payload, client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                showError(r.getMessage().isBlank()
                        ? "Unable to load your cart." : r.getMessage());
                payButton.setDisable(true);
                return;
            }
            renderSummary(r);
        });

        t.setOnFailed(e -> {
            showError("Network error while loading cart.");
            payButton.setDisable(true);
        });

        runTask(t);
    }

    private void renderSummary(Response r) {
        if (summaryContainer == null) return;
        summaryContainer.getChildren().clear();

        JSONObject payload = r.getPayloadAsJsonObject();
        JSONArray items = payload.optJSONArray("items");

        if (items == null || items.length() == 0) {
            Label empty = new Label("Your cart is empty.");
            empty.getStyleClass().add("cart-empty-state");
            summaryContainer.getChildren().add(empty);
            payButton.setDisable(true);
            showError("Your cart is empty. Add items before checking out.");
            return;
        }

        double total = 0.0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            String name = it.optString("name", "Product");
            int qty = it.optInt("quantity", 1);
            double unitPrice = it.optDouble("unitPrice", 0.0);
            double subtotal = unitPrice * qty;
            total += subtotal;

            summaryContainer.getChildren().add(createSummaryRow(name, qty, unitPrice, subtotal));
        }

        if (subtotalLabel != null) subtotalLabel.setText(String.format("%.2f Dhs", total));
        if (totalLabel != null) totalLabel.setText(String.format("%.2f Dhs", total));
    }

    private HBox createSummaryRow(String name, int qty, double unitPrice, double subtotal) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("cart-item-title");
        nameLabel.setWrapText(true);
        nameLabel.setStyle("-fx-font-size: 14px;");

        Label qtyLabel = new Label("×" + qty);
        qtyLabel.getStyleClass().add("cart-item-category");

        Label priceLabel = new Label(String.format("%.2f Dhs", unitPrice));
        priceLabel.getStyleClass().add("cart-item-category");

        HBox leftGroup = new HBox(8, nameLabel, qtyLabel, priceLabel);
        leftGroup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftGroup, Priority.ALWAYS);

        Label subtotalLabel = new Label(String.format("%.2f Dhs", subtotal));
        subtotalLabel.getStyleClass().add("cart-item-unit-price");
        subtotalLabel.setStyle("-fx-font-size: 14px;");

        HBox row = new HBox(12, leftGroup, subtotalLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("cart-item-card");
        row.setPadding(new Insets(14, 18, 14, 18));
        return row;
    }

    // ── Payment flow ─────────────────────────────────────────────────────────

    @FXML
    private void handlePay() {
        hideStatus();

        // ── Step 1: Client-side validation ───────────────────────────────────
        String holder = cardHolderField.getText() == null ? "" : cardHolderField.getText().trim();
        String rawCard = cardNumberField.getText() == null ? "" : cardNumberField.getText().trim();
        String expiry = expiryField.getText() == null ? "" : expiryField.getText().trim();
        String cvv = cvvField.getText() == null ? "" : cvvField.getText().trim();

        // Remove spaces/dashes from card number for validation
        String cardDigits = rawCard.replaceAll("[\\s\\-]", "");

        if (holder.isEmpty()) {
            showError("Please enter the cardholder name.");
            return;
        }
        if (!cardDigits.matches("\\d{16}")) {
            showError("Card number must be exactly 16 digits.");
            return;
        }
        if (!expiry.matches("\\d{2}/\\d{2}")) {
            showError("Expiry must be in MM/YY format.");
            return;
        }
        // Check if card is expired
        try {
            int month = Integer.parseInt(expiry.substring(0, 2));
            int year = 2000 + Integer.parseInt(expiry.substring(3, 5));
            if (month < 1 || month > 12) {
                showError("Invalid expiry month (must be 01–12).");
                return;
            }
            YearMonth cardExpiry = YearMonth.of(year, month);
            if (cardExpiry.isBefore(YearMonth.now())) {
                showError("This card has expired.");
                return;
            }
        } catch (Exception ex) {
            showError("Invalid expiry date.");
            return;
        }
        if (!cvv.matches("\\d{3}")) {
            showError("CVV must be exactly 3 digits.");
            return;
        }

        payButton.setDisable(true);

        // Keep final copies for the lambda chain
        final String finalCardDigits = cardDigits;
        final String finalExpiry = expiry;
        final String finalCvv = cvv;

        // ── If we already placed the order (retry after failed payment) ──────
        if (pendingOrderId != null) {
            showInfo("Retrying payment…");
            sendPayment(pendingOrderId, finalCardDigits, finalExpiry, finalCvv);
            return;
        }

        // ── Step 2: PLACE_ORDER (first attempt only) ─────────────────────────
        showInfo("Placing your order…");

        Task<Response> placeOrderTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(
                        MessageProtocol.ACTION_PLACE_ORDER, payload, client.getSessionToken()));
            }
        };

        placeOrderTask.setOnSucceeded(e -> {
            Response orderResp = placeOrderTask.getValue();
            if (!orderResp.isSuccess()) {
                payButton.setDisable(false);
                showError(orderResp.getMessage().isBlank()
                        ? "Order failed." : orderResp.getMessage());
                return;
            }

            // Extract orderId from the response
            JSONObject orderPayload = orderResp.getPayloadAsJsonObject();
            String orderId = orderPayload.optString("orderId",
                    orderPayload.optString("order_id", ""));

            if (orderId.isEmpty()) {
                payButton.setDisable(false);
                showError("Order created but no order ID received.");
                return;
            }

            // Save orderId so retries skip PLACE_ORDER
            pendingOrderId = orderId;

            // ── Step 3: PAYMENT ──────────────────────────────────────────────
            showInfo("Processing payment…");
            sendPayment(orderId, finalCardDigits, finalExpiry, finalCvv);
        });

        placeOrderTask.setOnFailed(e -> {
            payButton.setDisable(false);
            showError("Network error while placing order.");
        });

        runTask(placeOrderTask);
    }

    private void sendPayment(String orderId, String cardNumber, String expiry, String cvv) {
        Task<Response> payTask = new Task<>() {
            @Override
            protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("order_id", orderId);
                payload.put("card_number", cardNumber);
                payload.put("expiry", expiry);
                payload.put("cvv", cvv);
                return client.send(new Request(
                        MessageProtocol.ACTION_PAYMENT, payload, client.getSessionToken()));
            }
        };

        payTask.setOnSucceeded(e -> {
            Response payResp = payTask.getValue();
            if (payResp.isSuccess()) {
                // Navigate to order history on success
                Platform.runLater(() -> SceneManager.showOrderHistory());
            } else {
                payButton.setDisable(false);
                String msg = payResp.getMessage();
                showError(msg == null || msg.isBlank()
                        ? "Payment declined. Please check your card details." : msg);
            }
        });

        payTask.setOnFailed(e -> {
            payButton.setDisable(false);
            showError("Network error during payment.");
        });

        runTask(payTask);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    @FXML
    private void handleBack() {
        SceneManager.showCart();
    }

    // ── Status message helpers ───────────────────────────────────────────────

    private void showError(String message) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusLabel.getStyleClass().setAll("global-error");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void showInfo(String message) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusLabel.getStyleClass().setAll("success-label");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void hideStatus() {
        if (statusLabel == null) return;
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}
