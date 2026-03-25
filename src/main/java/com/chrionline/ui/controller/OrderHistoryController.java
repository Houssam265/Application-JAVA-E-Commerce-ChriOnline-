package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JavaFX controller for displaying the current user's order history.
 *
 * Calls the server action {@link MessageProtocol#ACTION_GET_ORDERS} on load,
 * then renders one card per order into {@code orderListContainer}.
 */
public class OrderHistoryController {

    @FXML private VBox orderListContainer;
    @FXML private Label statusLabel;

    private static final DateTimeFormatter OUTPUT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        loadOrders();
    }

    private void loadOrders() {
        if (orderListContainer != null) {
            orderListContainer.getChildren().clear();
        }
        hideStatus();

        // Token is stored in Client after LOGIN/REGISTER (ClientSession has identity fields only).
        Client client = Client.getInstance();
        if (!client.hasSession()) {
            showError("You are not logged in. Please login again.");
            return;
        }

        Task<Response> t = new Task<>() {
            @Override
            protected Response call() throws Exception {
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(MessageProtocol.ACTION_GET_ORDERS, payload, client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (r == null) {
                showError("Empty response from server.");
                return;
            }
            if (!r.isSuccess()) {
                showError(r.getMessage().isBlank() ? "Server error while loading orders." : r.getMessage());
                return;
            }

            renderOrders(r.getPayload());
        });

        t.setOnFailed(e -> {
            Throwable ex = t.getException();
            showError(ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Network error while loading orders.");
        });

        runTask(t);
    }

    private void renderOrders(Object payload) {
        JSONArray ordersArr;
        if (payload == null) {
            ordersArr = new JSONArray();
        } else if (payload instanceof JSONArray) {
            ordersArr = (JSONArray) payload;
        } else {
            ordersArr = new JSONArray(payload.toString());
        }

        if (ordersArr.length() == 0) {
            showEmpty("You have no orders yet.");
            return;
        }

        hideStatus();
        for (int i = 0; i < ordersArr.length(); i++) {
            JSONObject orderJson = ordersArr.getJSONObject(i);
            orderListContainer.getChildren().add(createOrderCard(orderJson));
        }
    }

    private VBox createOrderCard(JSONObject orderJson) {
        String orderId = orderJson.optString("orderId", orderJson.optString("order_id", ""));
        double totalPrice = orderJson.optDouble("totalPrice",
                orderJson.optDouble("totalAmount", 0.0));
        String status = orderJson.optString("status", "PENDING");
        String createdAtRaw = orderJson.optString("createdAt", orderJson.optString("created_at", ""));
        String createdAtFormatted = formatCreatedAt(createdAtRaw);

        // Header row
        Label orderIdLabel = new Label("Order: " + (orderId.isBlank() ? "-" : orderId));
        orderIdLabel.getStyleClass().add("cart-item-title");

        Label dateLabel = new Label(createdAtFormatted.isBlank() ? "-" : createdAtFormatted);
        dateLabel.getStyleClass().add("cart-item-category");

        Label totalLabel = new Label(String.format("%.2f Dhs", totalPrice));
        totalLabel.getStyleClass().add("cart-item-unit-price");

        Label statusBadge = new Label(status);
        statusBadge.getStyleClass().add("status-pill");
        applyOrderStatusStyle(statusBadge, status);

        HBox header = new HBox(12, orderIdLabel, dateLabel, totalLabel, statusBadge);
        header.setFillHeight(true);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().add(2, spacer); // put spacer between date and total

        VBox card = new VBox(12);
        card.getStyleClass().add("cart-item-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPadding(new Insets(18, 18, 18, 18));
        card.getChildren().add(header);

        // Optional items detail (only if server returns non-empty items)
        JSONArray itemsArr = orderJson.optJSONArray("items");
        if (itemsArr != null && itemsArr.length() > 0) {
            VBox detailsBox = new VBox(8);
            detailsBox.setPadding(new Insets(10, 0, 0, 0));
            detailsBox.setVisible(false);
            detailsBox.setManaged(false);

            for (int i = 0; i < itemsArr.length(); i++) {
                JSONObject itemJson = itemsArr.getJSONObject(i);
                VBox itemRow = createOrderItemRow(itemJson);
                detailsBox.getChildren().add(itemRow);
            }

            Button toggle = new Button("Show items");
            toggle.getStyleClass().add("btn-outline");

            final VBox detailsRef = detailsBox;
            toggle.setOnAction(ev -> {
                boolean expanded = !detailsRef.isVisible();
                detailsRef.setVisible(expanded);
                detailsRef.setManaged(expanded);
                toggle.setText(expanded ? "Hide items" : "Show items");
            });

            card.getChildren().addAll(toggle, detailsBox);
        }

        return card;
    }

    private VBox createOrderItemRow(JSONObject itemJson) {
        String productName = itemJson.optString("productName",
                itemJson.optString("product_name", ""));
        int productId = itemJson.optInt("productId", itemJson.optInt("product_id", 0));
        if (productName == null || productName.isBlank() || "null".equals(productName)) {
            productName = productId > 0 ? "Product #" + productId : "Product";
        }

        int quantity = itemJson.optInt("quantity", 0);
        double unitPrice = itemJson.optDouble("unitPrice", itemJson.optDouble("unit_price", 0.0));

        Label nameLabel = new Label(productName);
        nameLabel.getStyleClass().add("body-text");
        nameLabel.setWrapText(true);

        Label qtyLabel = new Label("Qty: " + quantity);
        qtyLabel.getStyleClass().add("cart-item-category");

        Label unitPriceLabel = new Label(String.format("%.2f Dhs", unitPrice));
        unitPriceLabel.getStyleClass().add("cart-item-unit-price");

        HBox row = new HBox(12, nameLabel, qtyLabel, unitPriceLabel);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        VBox wrap = new VBox(row);
        wrap.setFillWidth(true);
        return wrap;
    }

    private void applyOrderStatusStyle(Label badge, String statusRaw) {
        String s = statusRaw == null ? "" : statusRaw.trim().toUpperCase();

        // Keep status-pill shape; override colors via inline CSS.
        switch (s) {
            case "PENDING":
                badge.setStyle("-fx-text-fill: #b45309; -fx-background-color: rgba(251, 191, 36, 0.25);"
                        + " -fx-border-color: rgba(245, 158, 11, 0.45);");
                break;
            case "VALIDATED":
            case "CONFIRMED":
            case "PAID":
            case "SHIPPED":
            case "DELIVERED":
                badge.setStyle("-fx-text-fill: #16a34a; -fx-background-color: rgba(34, 197, 94, 0.12);"
                        + " -fx-border-color: rgba(34, 197, 94, 0.35);");
                break;
            case "CANCELLED":
            case "CANCELED":
                badge.setStyle("-fx-text-fill: #dc2626; -fx-background-color: rgba(239, 68, 68, 0.10);"
                        + " -fx-border-color: rgba(239, 68, 68, 0.35);");
                break;
            default:
                badge.setStyle("-fx-text-fill: #64748b; -fx-background-color: rgba(226, 232, 240, 0.7);"
                        + " -fx-border-color: rgba(226, 232, 240, 1.0);");
                break;
        }
    }

    private String formatCreatedAt(String createdAtRaw) {
        if (createdAtRaw == null || createdAtRaw.isBlank()) {
            return "";
        }
        try {
            LocalDateTime dt = LocalDateTime.parse(createdAtRaw);
            return dt.format(OUTPUT_DATE);
        } catch (Exception ignored) {
            return createdAtRaw;
        }
    }

    private void showEmpty(String message) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusLabel.getStyleClass().setAll("empty-state");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void showError(String message) {
        if (statusLabel == null) return;
        statusLabel.setText(message == null || message.isBlank() ? "Error while loading orders." : message);
        statusLabel.getStyleClass().setAll("global-error");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void hideStatus() {
        if (statusLabel == null) return;
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    @FXML
    private void handleBack() {
        SceneManager.showHome();
    }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}
