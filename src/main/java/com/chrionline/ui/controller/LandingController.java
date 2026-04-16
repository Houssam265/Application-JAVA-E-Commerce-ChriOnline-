package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

public class LandingController {

    @FXML private Button bellButton;
    @FXML private Button navHomeBtn;
    @FXML private Button navCatalogueBtn;
    @FXML private TextField headerSearchField;
    @FXML private MenuButton accountMenuButton;
    @FXML private MenuItem accountProfileItem;
    @FXML private MenuItem accountOrdersItem;
    @FXML private MenuItem accountAdminItem;
    @FXML private MenuItem accountLogoutItem;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<com.chrionline.ui.notifications.AppNotification> notificationsList;
    @FXML private javafx.scene.layout.FlowPane productsGrid;
    @FXML private javafx.scene.layout.FlowPane recentProductsGrid;

    private List<Product> topSellingProducts = new ArrayList<>();
    private List<Product> recentProducts = new ArrayList<>();

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        configureAccountMenu(session);
        bindNotifications();
        setActiveNav();
        loadTopSellingProducts();
        loadRecentProducts();
    }

    private void bindNotifications() {
        com.chrionline.ui.notifications.NotificationCenter nc = com.chrionline.ui.notifications.NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setFixedCellSize(-1);
            notificationsList.setCellFactory(lv -> new ListCell<>() {
                {
                    setOnMouseClicked(e -> {
                        com.chrionline.ui.notifications.AppNotification n = getItem();
                        if (n != null && !n.isRead()) {
                            com.chrionline.ui.notifications.NotificationCenter.getInstance().markAsRead(n);
                            updateItem(n, false);
                            notificationsList.refresh();
                        }
                    });
                    setPrefWidth(0);
                    setStyle("-fx-cursor: hand; -fx-padding: 8px 12px;");
                }

                @Override
                protected void updateItem(com.chrionline.ui.notifications.AppNotification item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("-fx-padding: 0;");
                        return;
                    }

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.TOP_LEFT);

                    javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5);
                    dot.setFill(item.isRead()
                            ? javafx.scene.paint.Color.TRANSPARENT
                            : javafx.scene.paint.Color.web("#f46a3d"));

                    VBox dotBox = new VBox(dot);
                    dotBox.setAlignment(Pos.TOP_CENTER);
                    dotBox.setPadding(new Insets(3, 0, 0, 0));

                    VBox textBox = new VBox(2);
                    textBox.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(textBox, Priority.ALWAYS);
                    row.setMaxWidth(Double.MAX_VALUE);

                    String hhmm = item.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    Label timeLabel = new Label(hhmm);
                    timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");

                    Label msgLabel = new Label(item.getMessage());
                    msgLabel.setWrapText(true);
                    msgLabel.setMaxWidth(Double.MAX_VALUE);
                    msgLabel.setPrefWidth(Math.max(220, lv.getWidth() - 90));
                    msgLabel.setStyle(item.isRead()
                            ? "-fx-font-size: 12px; -fx-text-fill: #94a3b8;"
                            : "-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-font-weight: 600;");

                    textBox.getChildren().addAll(timeLabel, msgLabel);
                    row.getChildren().addAll(dotBox, textBox);

                    setText(null);
                    setGraphic(row);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setPrefHeight(USE_COMPUTED_SIZE);
                    setMinHeight(USE_PREF_SIZE);
                    setOpacity(item.isRead() ? 0.65 : 1.0);
                    setStyle("-fx-cursor: hand; -fx-padding: 8px 12px; -fx-background-color: "
                            + (item.isRead() ? "transparent" : "rgba(244,106,61,0.04)") + ";");
                }
            });
        }

        nc.getNotifications().addListener((ListChangeListener<com.chrionline.ui.notifications.AppNotification>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (com.chrionline.ui.notifications.AppNotification n : c.getAddedSubList()) {
                        showToast("Notification", n.getMessage());
                    }
                }
            }
            refreshNotificationsMenuList();
        });
        refreshNotificationsMenuList();
    }

    private void refreshNotificationsMenuList() {
        com.chrionline.ui.notifications.NotificationCenter nc = com.chrionline.ui.notifications.NotificationCenter.getInstance();
        Platform.runLater(() -> {
            if (notificationsList == null) return;
            notificationsList.getItems().setAll(nc.getNotifications());
            notificationsList.refresh();
        });
    }

    private void setActiveNav() {
        if (navHomeBtn != null && !navHomeBtn.getStyleClass().contains("nav-pill-active")) {
            navHomeBtn.getStyleClass().add("nav-pill-active");
        }
        if (navCatalogueBtn != null) {
            navCatalogueBtn.getStyleClass().remove("nav-pill-active");
        }
    }

    private void configureAccountMenu(ClientSession session) {
        if (accountMenuButton == null) return;
        String username = session.getUsername() == null || session.getUsername().isBlank()
                ? "Utilisateur"
                : session.getUsername();
        accountMenuButton.setText("Bonjour, " + username);

        if (accountProfileItem != null) {
            accountProfileItem.setGraphic(new FontIcon("fas-user"));
            accountProfileItem.setOnAction(e -> handleOpenProfile());
        }
        if (accountOrdersItem != null) {
            accountOrdersItem.setGraphic(new FontIcon("fas-box-open"));
            accountOrdersItem.setOnAction(e -> handleOpenOrders());
        }
        if (accountAdminItem != null) {
            accountAdminItem.setGraphic(new FontIcon("fas-user-shield"));
            accountAdminItem.setOnAction(e -> handleOpenAdmin());
            boolean isAdmin = session.isAdmin();
            accountAdminItem.setVisible(isAdmin);
            accountAdminItem.setDisable(!isAdmin);
        }
        if (accountLogoutItem != null) {
            accountLogoutItem.setGraphic(new FontIcon("fas-sign-out-alt"));
            accountLogoutItem.setOnAction(e -> handleLogout());
        }
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

    private void loadTopSellingProducts() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("limit", 4);
                Response response = client.send(new Request(
                        MessageProtocol.ACTION_GET_TOP_SELLING_PRODUCTS,
                        payload,
                        client.getSessionToken()
                ));
                if (response.isSuccess()) {
                    applyProducts(response.getPayload(), true);
                } else {
                    Platform.runLater(() -> displayProducts(productsGrid, List.of(), "Aucun produit populaire disponible pour le moment."));
                }
                return null;
            }
        };
        runTask(task);
    }

    private void loadRecentProducts() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("limit", 4);
                Response response = client.send(new Request(
                        MessageProtocol.ACTION_GET_RECENT_PRODUCTS,
                        payload,
                        client.getSessionToken()
                ));
                if (response.isSuccess()) {
                    applyProducts(response.getPayload(), false);
                } else {
                    Platform.runLater(() -> displayProducts(recentProductsGrid, List.of(), "Aucune nouveaute disponible pour le moment."));
                }
                return null;
            }
        };
        runTask(task);
    }

    private void applyProducts(Object payload, boolean topSelling) {
        try {
            org.json.JSONArray arr = payload instanceof org.json.JSONArray
                    ? (org.json.JSONArray) payload
                    : new org.json.JSONArray(String.valueOf(payload));
            List<Product> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject p = arr.getJSONObject(i);
                Product pr = new Product();
                pr.setProductId(p.optInt("productId", p.optInt("product_id", 0)));
                pr.setCategoryId(p.optInt("categoryId", p.optInt("category_id", 0)));
                pr.setName(p.optString("name", "Produit"));
                pr.setDescription(p.optString("description", ""));
                pr.setPrice(p.optDouble("price", 0.0));
                pr.setStock(p.optInt("stock", 0));
                pr.setAvailable(p.optBoolean("available", p.optBoolean("isAvailable", pr.getStock() > 0)));
                pr.setImageUrl(p.optString("imageUrl", p.optString("image_url", "")));
                org.json.JSONArray images = p.optJSONArray("imageUrls");
                if (images == null) images = p.optJSONArray("image_urls");
                if (images != null) {
                    List<String> urls = new ArrayList<>();
                    for (int j = 0; j < images.length(); j++) {
                        String url = images.optString(j, "");
                        if (!url.isBlank()) urls.add(url);
                    }
                    pr.setImageUrls(urls);
                }
                list.add(pr);
            }
            Platform.runLater(() -> {
                if (topSelling) {
                    topSellingProducts = list;
                    displayProducts(productsGrid, topSellingProducts, "Aucun produit populaire disponible pour le moment.");
                } else {
                    recentProducts = list;
                    displayProducts(recentProductsGrid, recentProducts, "Aucune nouveaute disponible pour le moment.");
                }
            });
        } catch (Exception ignored) {
            Platform.runLater(() -> {
                if (topSelling) {
                    displayProducts(productsGrid, List.of(), "Aucun produit populaire disponible pour le moment.");
                } else {
                    displayProducts(recentProductsGrid, List.of(), "Aucune nouveaute disponible pour le moment.");
                }
            });
        }
    }

    private void displayProducts(javafx.scene.layout.FlowPane targetGrid, List<Product> products, String emptyMessage) {
        if (targetGrid == null) return;
        targetGrid.getChildren().clear();

        if (products.isEmpty()) {
            Label noResults = new Label(emptyMessage);
            noResults.getStyleClass().addAll("body-text", "empty-state");
            targetGrid.getChildren().add(noResults);
            return;
        }

        for (Product product : products) {
            targetGrid.getChildren().add(createProductCard(product));
        }
    }

    private VBox createProductCard(Product p) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("product-card", "product-card--rich", "product-card-clickable");
        card.setPrefWidth(280);
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> SceneManager.showProductDetail(p));

        Label categoryLabel = new Label("Top Seller");
        categoryLabel.getStyleClass().add("product-category");

        Label stockLabel = new Label();
        boolean isAvailable = p.isAvailable() && p.getStock() > 0;
        if (isAvailable) {
            stockLabel.setText("En stock · " + p.getStock());
            stockLabel.getStyleClass().add("badge-instock");
        } else {
            stockLabel.setText("Rupture");
            stockLabel.getStyleClass().add("badge-outstock");
        }

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, categoryLabel, headerSpacer, stockLabel);

        StackPane media = new StackPane();
        media.getStyleClass().add("product-media");
        media.setPrefHeight(130);

        ImageView img = new ImageView();
        img.getStyleClass().add("product-image");
        img.setPreserveRatio(true);
        img.setFitWidth(232);
        img.setFitHeight(130);
        img.setSmooth(true);
        img.setCache(true);

        Label placeholder = new Label("Aucune image");
        placeholder.getStyleClass().add("image-placeholder");

        String url = p.getImageUrl();
        if (url != null) url = url.trim();
        if (url != null && !url.isBlank() && !"null".equalsIgnoreCase(url)) {
            try {
                String normalized = normalizeImageUrlForLocalFiles(url);
                Image image = new Image(normalized, true);
                img.setImage(image);
                image.progressProperty().addListener((obs, ov, nv) -> {
                    if (nv != null && nv.doubleValue() >= 1.0) {
                        Platform.runLater(() -> applyCoverViewport(img, image, img.getFitWidth(), img.getFitHeight()));
                    }
                });
                placeholder.setVisible(false);
                placeholder.setManaged(false);
            } catch (Exception ignored) {
            }
        }
        media.getChildren().addAll(img, placeholder);

        Label nameLabel = new Label(p.getName());
        nameLabel.getStyleClass().add("product-title");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(260);

        Label priceLabel = new Label(String.format("%.2f Dhs", p.getPrice()));
        priceLabel.getStyleClass().add("product-price");

        String rawDesc = p.getDescription() == null ? "" : p.getDescription().trim();
        Label descLabel = new Label(rawDesc);
        descLabel.getStyleClass().addAll("product-desc", "body-text");
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(48);
        if (rawDesc.isEmpty()) {
            descLabel.setVisible(false);
            descLabel.setManaged(false);
        }

        card.getChildren().addAll(header, media, nameLabel, priceLabel, descLabel);
        return card;
    }

    private static void applyCoverViewport(ImageView view, Image image, double targetW, double targetH) {
        if (view == null || image == null) return;
        double iw = image.getWidth();
        double ih = image.getHeight();
        if (iw <= 0 || ih <= 0 || targetW <= 0 || targetH <= 0) return;

        double targetRatio = targetW / targetH;
        double imageRatio = iw / ih;

        double vw, vh, vx, vy;
        if (imageRatio > targetRatio) {
            vh = ih;
            vw = ih * targetRatio;
            vx = (iw - vw) / 2.0;
            vy = 0;
        } else {
            vw = iw;
            vh = iw / targetRatio;
            vx = 0;
            vy = (ih - vh) / 2.0;
        }
        view.setViewport(new Rectangle2D(vx, vy, vw, vh));
    }

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;
        String lower = u.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return u;
        return new java.io.File(u).toURI().toString();
    }

    @FXML
    private void handleShopNow() {
        handleHeaderSearch();
    }

    @FXML
    private void handleOpenCatalogue() {
        SceneManager.showCatalogue();
    }

    @FXML
    private void handleHeaderSearch() {
        String query = headerSearchField == null || headerSearchField.getText() == null
                ? ""
                : headerSearchField.getText().trim();
        SceneManager.showCatalogue(query);
    }

    @FXML
    private void handleOpenCart() {
        SceneManager.showCart();
    }

    @FXML
    private void handleOpenAdmin() {
        SceneManager.showAdmin();
    }

    @FXML
    private void handleOpenProfile() {
        SceneManager.showProfile();
    }

    @FXML
    private void handleOpenOrders() {
        SceneManager.showOrderHistory();
    }

    @FXML
    private void handleLogout() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_LOGOUT, new org.json.JSONObject(), client.getSessionToken()));
            }
        };

        t.setOnSucceeded(e -> {
            Client.getInstance().disconnect();
            ClientSession.getInstance().clear();
            SceneManager.showLogin();
        });
        t.setOnFailed(e -> {
            if (bellButton != null && bellButton.getScene() != null) {
                ErrorHandler.showServerUnavailableBanner(bellButton.getScene(), this::handleLogout);
            }
        });
        runTask(t);
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
        com.chrionline.ui.notifications.NotificationCenter.getInstance().markAllAsRead();
        refreshNotificationsMenuList();
    }

    @FXML
    private void noop() {
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
        PauseTransition stay = new PauseTransition(javafx.util.Duration.seconds(4));
        Timeline out = new Timeline(
                new KeyFrame(javafx.util.Duration.millis(220),
                        new KeyValue(card.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(card.translateXProperty(), 56, Interpolator.EASE_IN))
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
