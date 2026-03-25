package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.notifications.AppNotification;
import com.chrionline.ui.notifications.NotificationCenter;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderHistoryController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private VBox cardsContainer;

    // Notifications
    @FXML private Button bellButton;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<AppNotification> notificationsList;

    // Navbar active state
    @FXML private Button navOrdersBtn;

    // Details
    @FXML private javafx.scene.layout.StackPane detailsOverlay;
    @FXML private VBox detailsCard;
    @FXML private Label detailsTitle;
    @FXML private Label detailsMeta;
    @FXML private ListView<String> detailsItems;
    @FXML private VBox timelineBox;

    private final List<JSONObject> allOrders = new ArrayList<>();

    private Scene getSceneOrNull() {
        if (cardsContainer != null && cardsContainer.getScene() != null) return cardsContainer.getScene();
        if (drawerPanel != null && drawerPanel.getScene() != null) return drawerPanel.getScene();
        return null;
    }

    private void handleTcpFailure(Throwable cause, String bannerMessage, Runnable retry) {
        Scene sc = getSceneOrNull();
        // Server unavailable: ONLY top banner (no dialog, no inline label).
        if (sc != null) {
            ErrorHandler.showServerUnavailableBanner(sc, retry);
        }
    }

    @FXML
    public void initialize() {
        setupFilter();
        bindNotifications();
        setActiveNav();
        loadOrders();
    }

    private void setupFilter() {
        if (statusFilter == null) return;
        statusFilter.getItems().addAll("TOUS", "EN_ATTENTE", "VALIDEE", "EXPEDIEE", "LIVREE");
        statusFilter.getSelectionModel().select("TOUS");
        statusFilter.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> rebuildCards());
    }

    private void bindNotifications() {
        NotificationCenter nc = NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
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
        if (navOrdersBtn != null && !navOrdersBtn.getStyleClass().contains("nav-pill-active")) {
            navOrdersBtn.getStyleClass().add("nav-pill-active");
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

    @FXML private void goHome() { SceneManager.showHome(); }
    @FXML private void goCart() { SceneManager.showCart(); }
    @FXML private void goProfile() { SceneManager.showProfile(); }

    private void loadOrders() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_GET_ORDERS, new JSONObject(), client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                String titleMsg = msg.isBlank() ? "Impossible de charger les commandes." : msg;
                ErrorHandler.showErrorDialog("Erreur", titleMsg);
                return;
            }
            allOrders.clear();
            Object payload = r.getPayload();
            JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(payload.toString());
            for (int i = 0; i < arr.length(); i++) {
                allOrders.add(arr.getJSONObject(i));
            }
            rebuildCards();
        });
        t.setOnFailed(e -> handleTcpFailure(((Task<?>) e.getSource()).getException(), "Serveur indisponible — Nouvelle tentative dans 10s...", this::loadOrders));
        runTask(t);
    }

    private void rebuildCards() {
        if (cardsContainer == null) return;
        cardsContainer.getChildren().clear();

        String filter = statusFilter != null ? statusFilter.getSelectionModel().getSelectedItem() : "TOUS";
        for (JSONObject o : allOrders) {
            String st = o.optString("status", "PENDING");
            String mapped = mapStatusToFrench(st);
            if (!"TOUS".equals(filter) && !filter.equals(mapped)) continue;
            cardsContainer.getChildren().add(createOrderCard(o));
        }

        if (cardsContainer.getChildren().isEmpty()) {
            Label empty = new Label("Aucune commande.");
            empty.getStyleClass().add("empty-state");
            cardsContainer.getChildren().add(empty);
        }
    }

    private VBox createOrderCard(JSONObject o) {
        String orderId = o.optString("orderId", o.optString("order_id", ""));
        double total = o.optDouble("totalAmount", o.optDouble("total_amount", 0.0));
        String statusRaw = o.optString("status", "PENDING");
        String statusFr = mapStatusToFrench(statusRaw);

        String dateStr = "—";
        String createdAt = o.optString("createdAt", o.optString("created_at", ""));
        if (!createdAt.isBlank()) {
            dateStr = createdAt.length() >= 16 ? createdAt.substring(0, 16).replace('T', ' ') : createdAt;
        }

        VBox card = new VBox(10);
        card.getStyleClass().add("auth-card");
        card.setStyle("-fx-padding: 16px 18px;");

        HBox top = new HBox(10);
        Label id = new Label("Order · " + (orderId.isBlank() ? "—" : orderId));
        id.setStyle("-fx-text-fill: #334155; -fx-font-weight: 800;");
        Label date = new Label(dateStr);
        date.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600;");
        HBox.setHgrow(date, javafx.scene.layout.Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label badge = new Label(statusFr);
        badge.getStyleClass().addAll("order-status-pill", pillForStatus(statusFr));

        top.getChildren().addAll(id, spacer, date, badge);

        Label totalLbl = new Label(String.format(Locale.US, "Total: %.2f Dhs", total));
        totalLbl.getStyleClass().add("body-text");

        Label hint = new Label("Cliquer pour voir le détail");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600; -fx-font-size: 12px;");

        card.getChildren().addAll(top, totalLbl, hint);
        card.setOnMouseClicked(e -> openDetails(orderId));
        card.setCursor(javafx.scene.Cursor.HAND);
        return card;
    }

    private void openDetails(String orderId) {
        if (orderId == null || orderId.isBlank()) return;

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
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                String titleMsg = msg.isBlank() ? "Impossible de charger le détail." : msg;
                ErrorHandler.showErrorDialog("Erreur", titleMsg);
                return;
            }
            JSONObject payload = r.getPayloadAsJsonObject();
            JSONObject order = payload.optJSONObject("order");
            if (order == null) {
                // gson may serialize order as map-like
                try { order = new JSONObject(payload.get("order").toString()); } catch (Exception ignored) {}
            }
            showDetails(orderId, payload, order);
        });
        t.setOnFailed(e -> handleTcpFailure(((Task<?>) e.getSource()).getException(), "Serveur indisponible — Nouvelle tentative dans 10s...", () -> openDetails(orderId)));
        runTask(t);
    }

    private void showDetails(String orderId, JSONObject payload, JSONObject order) {
        if (detailsOverlay == null) return;

        detailsTitle.setText("Order · " + orderId);
        String addr = payload.optString("shippingAddress", "—");
        String status = order != null ? mapStatusToFrench(order.optString("status", "PENDING")) : "—";
        detailsMeta.setText("Statut: " + status + " · Adresse: " + addr);

        detailsItems.getItems().clear();
        if (order != null && order.has("items") && !order.isNull("items")) {
            try {
                JSONArray items = order.get("items") instanceof JSONArray ? order.getJSONArray("items") : new JSONArray(order.get("items").toString());
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    int qty = it.optInt("quantity", 1);
                    double unit = it.optDouble("unitPrice", it.optDouble("unit_price", 0.0));
                    int pid = it.optInt("productId", it.optInt("product_id", 0));
                    detailsItems.getItems().add("Produit #" + pid + " · x" + qty + " · " + String.format(Locale.US, "%.2f Dhs", unit));
                }
            } catch (Exception ignored) {
                detailsItems.getItems().add("Items indisponibles.");
            }
        } else {
            detailsItems.getItems().add("Items indisponibles.");
        }

        timelineBox.getChildren().clear();
        JSONArray timeline = payload.optJSONArray("timeline");
        if (timeline != null) {
            for (int i = 0; i < timeline.length(); i++) {
                JSONObject s = timeline.getJSONObject(i);
                String st = s.optString("status", "");
                String at = s.optString("at", "");
                String line = st + (at.isBlank() ? "" : (" · " + at.replace('T', ' ')));
                Label l = new Label(line);
                l.getStyleClass().add("body-text");
                timelineBox.getChildren().add(stepperRow(i == timeline.length() - 1, l));
            }
        }

        detailsOverlay.setOpacity(0);
        detailsOverlay.setVisible(true);
        detailsOverlay.setManaged(true);
        Timeline in = new Timeline(new KeyFrame(javafx.util.Duration.millis(180),
                new KeyValue(detailsOverlay.opacityProperty(), 1, Interpolator.EASE_OUT)));
        in.play();
    }

    private HBox stepperRow(boolean last, Label content) {
        VBox rail = new VBox();
        rail.setPrefWidth(18);
        rail.setMinWidth(18);
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: rgba(255,122,69,0.95); -fx-font-size: 12px;");
        Region line = new Region();
        line.setPrefWidth(2);
        line.setMinWidth(2);
        line.setStyle("-fx-background-color: rgba(148,163,184,0.45); -fx-pref-width: 2px;");
        VBox.setVgrow(line, javafx.scene.layout.Priority.ALWAYS);
        rail.getChildren().addAll(dot);
        if (!last) rail.getChildren().add(line);

        HBox row = new HBox(10, rail, content);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        return row;
    }

    @FXML private void closeDetails() {
        if (detailsOverlay == null) return;
        Timeline out = new Timeline(new KeyFrame(javafx.util.Duration.millis(160),
                new KeyValue(detailsOverlay.opacityProperty(), 0, Interpolator.EASE_IN)));
        out.setOnFinished(e -> {
            detailsOverlay.setVisible(false);
            detailsOverlay.setManaged(false);
        });
        out.play();
    }

    private String mapStatusToFrench(String statusRaw) {
        return switch (statusRaw) {
            case "PENDING" -> "EN_ATTENTE";
            case "VALIDATED" -> "VALIDEE";
            case "SHIPPED" -> "EXPEDIEE";
            case "DELIVERED" -> "LIVREE";
            default -> "EN_ATTENTE";
        };
    }

    private String pillForStatus(String fr) {
        return switch (fr) {
            case "EN_ATTENTE" -> "pill-pending";
            case "VALIDEE" -> "pill-shipped";   // blue-ish
            case "EXPEDIEE" -> "pill-delivered"; // purple-ish
            case "LIVREE" -> "pill-validated";  // green
            default -> "pill-pending";
        };
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

