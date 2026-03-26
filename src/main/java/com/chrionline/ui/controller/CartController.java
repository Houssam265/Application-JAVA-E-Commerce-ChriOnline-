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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ContentDisplay;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Panier — mise en page deux colonnes (liste + récap), style e-commerce moderne.
 */
public class CartController {

    public static final class CartRow {
        private final IntegerProperty cartItemId = new SimpleIntegerProperty();
        private final IntegerProperty productId  = new SimpleIntegerProperty();
        private final StringProperty  name       = new SimpleStringProperty();
        private final StringProperty  categoryName = new SimpleStringProperty("");
        private final StringProperty  imageUrl     = new SimpleStringProperty("");
        private final DoubleProperty  unitPrice  = new SimpleDoubleProperty();
        private final IntegerProperty quantity   = new SimpleIntegerProperty();

        public int getCartItemId() { return cartItemId.get(); }
        public IntegerProperty cartItemIdProperty() { return cartItemId; }
        public int getProductId() { return productId.get(); }
        public IntegerProperty productIdProperty() { return productId; }
        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        public String getCategoryName() { return categoryName.get(); }
        public StringProperty categoryNameProperty() { return categoryName; }
        public String getImageUrl() { return imageUrl.get(); }
        public StringProperty imageUrlProperty() { return imageUrl; }
        public double getUnitPrice() { return unitPrice.get(); }
        public DoubleProperty unitPriceProperty() { return unitPrice; }
        public int getQuantity() { return quantity.get(); }
        public IntegerProperty quantityProperty() { return quantity; }

        public double getSubtotal() { return getUnitPrice() * getQuantity(); }
    }

    @FXML private VBox cartItemsContainer;
    @FXML private Label subtotalLabel;
    @FXML private Label shippingLabel;
    @FXML private Label taxLabel;
    @FXML private Label grandTotalLabel;
    @FXML private Button checkoutButton;
    @FXML private Label messageLabel;
    @FXML private Button navCartBtn;
    @FXML private Button adminButton;
    @FXML private Button bellButton;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<AppNotification> notificationsList;

    private final ObservableList<CartRow> rows = FXCollections.observableArrayList();

    /** TVA affichée (0 % par défaut ; modifier si besoin). */
    private static final double TAX_RATE = 0.0;

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        if (session.isAdmin() && adminButton != null) {
            adminButton.setVisible(true);
            adminButton.setManaged(true);
        }
        if (navCartBtn != null && !navCartBtn.getStyleClass().contains("nav-pill-active")) {
            navCartBtn.getStyleClass().add("nav-pill-active");
        }
        bindNotifications();
        rows.addListener((javafx.collections.ListChangeListener<CartRow>) c -> rebuildCartItems());
        rebuildCartItems();
        refreshCart();
    }

    private void bindNotifications() {
        NotificationCenter nc = NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setFixedCellSize(-1);
            notificationsList.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(AppNotification item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    HBox row = new HBox(8);
                    row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
                    row.setMaxWidth(Double.MAX_VALUE);

                    VBox textBox = new VBox(2);
                    textBox.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);

                    String hhmm = item.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    Label timeLabel = new Label(hhmm);
                    timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");

                    Label msgLabel = new Label(item.getMessage());
                    msgLabel.setWrapText(true);
                    msgLabel.setMaxWidth(Double.MAX_VALUE);
                    msgLabel.setPrefWidth(Math.max(220, lv.getWidth() - 70));
                    msgLabel.setStyle(item.isRead()
                            ? "-fx-font-size: 12px; -fx-text-fill: #94a3b8;"
                            : "-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-font-weight: 600;");

                    textBox.getChildren().addAll(timeLabel, msgLabel);
                    row.getChildren().add(textBox);

                    setText(null);
                    setGraphic(row);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setPrefHeight(USE_COMPUTED_SIZE);
                    setMinHeight(USE_PREF_SIZE);
                    setOpacity(item.isRead() ? 0.65 : 1.0);
                    setStyle("-fx-padding: 8px 12px;");
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

        Timeline in = new Timeline(
                new KeyFrame(javafx.util.Duration.millis(220),
                        new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(card.translateXProperty(), 0, Interpolator.EASE_OUT))
        );
        javafx.animation.PauseTransition stay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
        Timeline out = new Timeline(
                new KeyFrame(javafx.util.Duration.millis(220),
                        new KeyValue(card.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(card.translateXProperty(), 56, Interpolator.EASE_IN))
        );
        out.setOnFinished(e -> toastLayer.getChildren().remove(card));
        new javafx.animation.SequentialTransition(in, stay, out).play();
    }

    @FXML
    private void goHome() { SceneManager.showHome(); }

    @FXML
    private void goProfile() { SceneManager.showProfile(); }

    @FXML
    private void goOrders() { SceneManager.showOrderHistory(); }

    @FXML
    private void handleOpenAdmin() { SceneManager.showAdmin(); }

    @FXML
    private void noop() {
        // active page
    }

    @FXML
    private void handleLogout() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_LOGOUT, new JSONObject(), client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Client.getInstance().disconnect();
            ClientSession.getInstance().clear();
            SceneManager.showLogin();
        });
        t.setOnFailed(e -> {
            Scene scene = getSceneOrNull();
            if (scene != null) {
                ErrorHandler.showServerUnavailableBanner(scene, this::handleLogout);
            }
        });
        runTask(t);
    }

    private Scene getSceneOrNull() {
        if (messageLabel != null && messageLabel.getScene() != null) return messageLabel.getScene();
        if (checkoutButton != null && checkoutButton.getScene() != null) return checkoutButton.getScene();
        return null;
    }

    private void handleTcpFailure(Throwable cause, String bannerMessage, Runnable retryAction) {
        // Server unavailable: rule = ONLY top banner countdown.
        Scene scene = getSceneOrNull();
        if (scene != null && retryAction != null) {
            ErrorHandler.showServerUnavailableBanner(scene, retryAction);
        }
        // Hide inline/global message so we never show dialog+banner+inline at once.
        setMessage(null, false);
    }

    private void rebuildCartItems() {
        if (cartItemsContainer == null) {
            return;
        }
        cartItemsContainer.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("Your cart is empty.");
            empty.getStyleClass().add("cart-empty-state");
            cartItemsContainer.getChildren().add(empty);
            return;
        }
        for (CartRow row : rows) {
            cartItemsContainer.getChildren().add(createItemCard(row));
        }
    }

    private HBox createItemCard(CartRow row) {
        HBox card = new HBox(20);
        card.getStyleClass().add("cart-item-card");
        card.setAlignment(Pos.CENTER_LEFT);

        double imgSize = 104;
        StackPane imgWrap = new StackPane();
        imgWrap.setMinSize(imgSize, imgSize);
        imgWrap.setMaxSize(imgSize, imgSize);
        imgWrap.getStyleClass().add("cart-item-image-wrap");

        ImageView imgView = new ImageView();
        imgView.setFitWidth(imgSize);
        imgView.setFitHeight(imgSize);
        imgView.setPreserveRatio(true);
        imgView.setSmooth(true);
        Rectangle clip = new Rectangle(imgSize, imgSize);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imgView.setClip(clip);

        Label imgPh = new Label("No image");
        imgPh.getStyleClass().add("cart-item-image-placeholder");

        String url = row.getImageUrl();
        if (url != null) {
            url = url.trim();
        }
        if (url != null && !url.isBlank() && !"null".equalsIgnoreCase(url)) {
            try {
                String normalized = normalizeImageUrlForLocalFiles(url);
                Image im = new Image(normalized, true);
                imgView.setImage(im);
                imgPh.setVisible(false);
                imgPh.setManaged(false);
            } catch (Exception ignored) {
                imgView.setVisible(false);
            }
        } else {
            imgView.setVisible(false);
        }

        imgWrap.getChildren().addAll(imgView, imgPh);

        Label title = new Label(row.getName());
        title.getStyleClass().add("cart-item-title");
        title.setWrapText(true);

        String cat = row.getCategoryName();
        Label catLbl = new Label(cat == null || cat.isBlank() ? "—" : cat);
        catLbl.getStyleClass().add("cart-item-category");

        Button minus = new Button("−");
        minus.getStyleClass().add("cart-qty-btn");
        Label qtyVal = new Label(String.valueOf(row.getQuantity()));
        qtyVal.getStyleClass().add("cart-qty-value");
        Button plus = new Button("+");
        plus.getStyleClass().add("cart-qty-btn");

        HBox qtyBox = new HBox(0, minus, qtyVal, plus);
        qtyBox.getStyleClass().add("cart-qty-box");
        qtyBox.setAlignment(Pos.CENTER_LEFT);

        int q0 = row.getQuantity();
        minus.setDisable(q0 <= 1);
        minus.setOnAction(e -> {
            int q = row.getQuantity();
            if (q <= 1) return;
            int nq = q - 1;
            row.quantityProperty().set(nq);
            updateTotals();
            updateQuantityOnServer(row.getCartItemId(), nq);
            rebuildCartItems();
        });
        plus.setOnAction(e -> {
            int q = row.getQuantity();
            if (q >= 999) return;
            int nq = q + 1;
            row.quantityProperty().set(nq);
            updateTotals();
            updateQuantityOnServer(row.getCartItemId(), nq);
            rebuildCartItems();
        });

        VBox leftText = new VBox(8, title, catLbl, qtyBox);
        leftText.setFillWidth(true);
        HBox.setHgrow(leftText, Priority.ALWAYS);

        FontIcon trashIc = new FontIcon();
        trashIc.setIconLiteral("fas-trash-alt");
        trashIc.setIconSize(15);
        trashIc.getStyleClass().add("ikonli-danger");
        Button remove = new Button();
        remove.setGraphic(trashIc);
        remove.setTooltip(new Tooltip("Remove item"));
        remove.getStyleClass().add("btn-cart-remove-icon");
        remove.setCursor(Cursor.HAND);
        remove.setOnAction(e -> removeItem(row.getCartItemId()));

        Label unitPrice = new Label(String.format("%.2f Dhs", row.getUnitPrice()));
        unitPrice.getStyleClass().add("cart-item-unit-price");

        VBox rightCol = new VBox(8);
        rightCol.setAlignment(Pos.TOP_RIGHT);
        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        HBox topTrash = new HBox(remove);
        topTrash.setAlignment(Pos.TOP_RIGHT);
        VBox priceBox = new VBox(unitPrice);
        priceBox.setAlignment(Pos.BOTTOM_RIGHT);
        rightCol.getChildren().addAll(topTrash, sp, priceBox);

        card.getChildren().addAll(imgWrap, leftText, rightCol);
        return card;
    }

    @FXML
    private void handleContinueShopping() {
        SceneManager.showHome();
    }

    @FXML
    private void handleClearCart() {
        setMessage(null, false);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(MessageProtocol.ACTION_CLEAR_CART, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Impossible de vider le panier." : msg);
                setMessage(null, false);
                return;
            }
            rows.clear();
            updateTotals();
            setMessage("Panier vidé.", false);
        });
        t.setOnFailed(ev -> handleTcpFailure(((Task<?>) ev.getSource()).getException(), "Erreur réseau lors du vidage du panier.", this::handleClearCart));
        runTask(t);
    }

    @FXML
    private void handleCheckout() {
        setMessage(null, false);
        if (rows.isEmpty()) {
            setMessage("Votre panier est vide.", true);
            return;
        }
        checkoutButton.setDisable(true);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(MessageProtocol.ACTION_PLACE_ORDER, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            checkoutButton.setDisable(false);
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                if (msg.toLowerCase().contains("stock") && msg.toLowerCase().contains("insuff")) {
                    ErrorHandler.showWarningDialog("Stock insuffisant", msg);
                    setMessage(null, false);
                }
                else {
                    ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Commande échouée." : msg);
                    setMessage(null, false);
                }
                return;
            }
            // Extract orderId from payload so Checkout can pay it.
            try {
                JSONObject payload = r.getPayloadAsJsonObject();
                String orderId = payload.optString("orderId", payload.optString("order_id", ""));
                if (orderId.isBlank() && payload.has("order") && !payload.isNull("order")) {
                    Object orderObj = payload.get("order");
                    JSONObject orderJson = orderObj instanceof JSONObject ? (JSONObject) orderObj : new JSONObject(orderObj.toString());
                    orderId = orderJson.optString("orderId", orderJson.optString("order_id", ""));
                }
                if (!orderId.isBlank()) {
                    com.chrionline.ui.ClientSession.getInstance().setCurrentOrderId(orderId);
                }
            } catch (Exception ignored) {}

            setMessage("Commande créée. Redirection vers le paiement…", false);
            rows.clear();
            updateTotals();
            javafx.application.Platform.runLater(com.chrionline.ui.SceneManager::showCheckout);
        });
        t.setOnFailed(e -> {
            checkoutButton.setDisable(false);
            handleTcpFailure(((Task<?>) e.getSource()).getException(), "Erreur serveur — Nouvelle tentative dans 10s...", this::handleCheckout);
        });
        runTask(t);
    }

    private void refreshCart() {
        setMessage(null, false);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                return client.send(new Request(MessageProtocol.ACTION_GET_CART, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Impossible de charger le panier." : msg);
                setMessage(null, false);
                return;
            }
            rows.setAll(parseCartRows(r));
            updateTotals();
        });
        t.setOnFailed(e -> handleTcpFailure(((Task<?>) e.getSource()).getException(), "Serveur indisponible — Nouvelle tentative dans 10s...", this::refreshCart));
        runTask(t);
    }

    private ObservableList<CartRow> parseCartRows(Response r) {
        ObservableList<CartRow> list = FXCollections.observableArrayList();
        JSONObject payload = r.getPayloadAsJsonObject();
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return list;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            CartRow row = new CartRow();
            row.cartItemIdProperty().set(it.optInt("cartItemId", 0));
            row.productIdProperty().set(it.optInt("productId", 0));
            row.nameProperty().set(it.optString("name", "Produit"));
            row.unitPriceProperty().set(it.optDouble("unitPrice", 0.0));
            row.quantityProperty().set(it.optInt("quantity", 1));
            row.categoryNameProperty().set(it.optString("categoryName", ""));
            row.imageUrlProperty().set(it.optString("imageUrl", ""));
            list.add(row);
        }
        return list;
    }

    private void removeItem(int cartItemId) {
        setMessage(null, false);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("cart_item_id", cartItemId);
                return client.send(new Request(MessageProtocol.ACTION_REMOVE_FROM_CART, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Suppression impossible." : msg);
                setMessage(null, false);
                return;
            }
            rows.removeIf(x -> x.getCartItemId() == cartItemId);
            updateTotals();
        });
        t.setOnFailed(e -> handleTcpFailure(((Task<?>) e.getSource()).getException(), "Serveur indisponible — Nouvelle tentative dans 10s...", () -> removeItem(cartItemId)));
        runTask(t);
    }

    private void updateQuantityOnServer(int cartItemId, int newQty) {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("cart_item_id", cartItemId);
                payload.put("quantity", newQty);
                return client.send(new Request(MessageProtocol.ACTION_UPDATE_CART_ITEM, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                if (ErrorHandler.isSessionExpiredMessage(msg)) {
                    setMessage(null, false);
                    ErrorHandler.handleSessionExpired();
                    return;
                }
                ErrorHandler.showErrorDialog("Erreur", msg.isBlank() ? "Quantité non mise à jour côté serveur." : msg);
                setMessage(null, false);
                refreshCart();
            }
        });
        t.setOnFailed(e -> {
            handleTcpFailure(((Task<?>) e.getSource()).getException(), "Serveur indisponible — Nouvelle tentative dans 10s...", () -> updateQuantityOnServer(cartItemId, newQty));
        });
        runTask(t);
    }

    private void updateTotals() {
        double sub = rows.stream().mapToDouble(CartRow::getSubtotal).sum();
        double shipping = 0.0;
        double tax = sub * TAX_RATE;
        double grand = sub + shipping + tax;

        if (subtotalLabel != null) {
            subtotalLabel.setText(String.format("%.2f Dhs", sub));
        }
        if (shippingLabel != null) {
            shippingLabel.setText(shipping <= 0 ? "Free" : String.format("%.2f Dhs", shipping));
        }
        if (taxLabel != null) {
            taxLabel.setText(String.format("%.2f Dhs", tax));
        }
        if (grandTotalLabel != null) {
            grandTotalLabel.setText(String.format("%.2f Dhs", grand));
        }
        if (checkoutButton != null) {
            checkoutButton.setDisable(rows.isEmpty());
        }
    }

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;
        String lower = u.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return u;
        return new java.io.File(u).toURI().toString();
    }

    private void setMessage(String msg, boolean error) {
        if (messageLabel == null) return;
        if (msg == null || msg.isBlank()) {
            messageLabel.setText("");
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
            return;
        }
        messageLabel.setText(msg);
        messageLabel.getStyleClass().setAll(error ? "global-error" : "success-label");
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

}
