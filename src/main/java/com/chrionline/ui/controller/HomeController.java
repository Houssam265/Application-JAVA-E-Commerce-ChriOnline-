package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ErrorHandler;
import com.chrionline.ui.SceneManager;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur de l'écran d'Accueil / Catalogue (Home.fxml).
 *
 * Responsabilités (KAN-6) :
 * 1. Afficher la liste de tous les produits sous forme de grille (FlowPane).
 * 2. Afficher nom, prix, stock et catégorie.
 * 3. Filtrage par nom (recherche) et catégorie (pastilles issues de la BD).
 * 4. Clic sur une carte produit pour ouvrir le détail ; indicateur si stock=0.
 */
public class HomeController {

    @FXML private TextField searchField;
    @FXML private FlowPane categoryPills;
    @FXML private ScrollPane homeScroll;
    @FXML private FlowPane productsGrid;
    @FXML private VBox productsSection;
    @FXML private Button adminButton;
    @FXML private Button bellButton;
    @FXML private Button navHomeBtn;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<com.chrionline.ui.notifications.AppNotification> notificationsList;

    // TODO: A remplacer par un fetch via Socket/TCP au serveur lors du lien backend complet
    private List<Product> allProducts;
    private final Map<Integer, String> categoryNameById = new HashMap<>();
    private final Map<String, Integer> categoryIdByName = new HashMap<>();
    /** "Toutes" ou nom exact d'une catégorie renvoyée par le serveur. */
    private String selectedCategoryName = "Toutes";

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        if (session.isAdmin() && adminButton != null) {
            adminButton.setVisible(true);
            adminButton.setManaged(true);
        }

        allProducts = new ArrayList<>();

        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterProducts());

        // Load categories + products from server (DB)
        loadCatalogueFromServer();

        // Bind UDP notifications counter (KAN-31)
        bindNotifications();
        setActiveNav();
    }

    private void bindNotifications() {
        com.chrionline.ui.notifications.NotificationCenter nc = com.chrionline.ui.notifications.NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setFixedCellSize(-1);
            notificationsList.setCellFactory(lv -> new ListCell<>() {
                {
                    // Click on a cell → mark ONLY that notification as read
                    setOnMouseClicked(e -> {
                        com.chrionline.ui.notifications.AppNotification n = getItem();
                        if (n != null && !n.isRead()) {
                            com.chrionline.ui.notifications.NotificationCenter.getInstance().markAsRead(n);
                            updateItem(n, false); // redraw this cell immediately
                            notificationsList.refresh();
                        }
                    });
                    setPrefWidth(0); // allow the cell to shrink to list width
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

                    // ── Build card ──────────────────────────────────────
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.TOP_LEFT);

                    // Unread dot indicator
                    javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5);
                    dot.setFill(item.isRead()
                            ? javafx.scene.paint.Color.TRANSPARENT
                            : javafx.scene.paint.Color.web("#f46a3d"));

                    VBox dotBox = new VBox(dot);
                    dotBox.setAlignment(Pos.TOP_CENTER);
                    dotBox.setPadding(new Insets(3, 0, 0, 0));

                    // Text block
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
    private void handleShopNow() {
        if (homeScroll == null || productsSection == null || homeScroll.getContent() == null) return;
        Platform.runLater(() -> {
            javafx.scene.Node content = homeScroll.getContent();
            double contentHeight = content.getLayoutBounds().getHeight();
            double viewportHeight = homeScroll.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight) return;

            double contentMinY = content.localToScene(content.getBoundsInLocal()).getMinY();
            double targetMinY = productsSection.localToScene(productsSection.getBoundsInLocal()).getMinY();
            double y = Math.max(0, targetMinY - contentMinY - 12);
            double v = y / (contentHeight - viewportHeight);
            if (v < 0) v = 0;
            if (v > 1) v = 1;
            homeScroll.setVvalue(v);
        });
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

    private void updateUnreadBadge(int unread) {
        if (unreadBadge == null) return;
        javafx.application.Platform.runLater(() -> {
            unreadBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            boolean show = unread > 0;
            unreadBadge.setVisible(show);
            unreadBadge.setManaged(show);
        });
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
        // active page
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

    /**
     * Filtre les produits selon le texte de recherche et la catégorie sélectionnée.
     */
    private void filterProducts() {
        String searchText = searchField.getText().toLowerCase();

        int targetCategoryId = -1;
        if (selectedCategoryName != null && !"Toutes".equals(selectedCategoryName)) {
            targetCategoryId = categoryIdByName.getOrDefault(selectedCategoryName, -1);
        }

        int finalTargetId = targetCategoryId;
        List<Product> filteredList = allProducts.stream()
                .filter(p -> p.getName().toLowerCase().contains(searchText))
                .filter(p -> finalTargetId == -1 || p.getCategoryId() == finalTargetId)
                .collect(Collectors.toList());

        displayProducts(filteredList);
    }

    private void loadCatalogueFromServer() {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Client client = Client.getInstance();
                client.connect();

                Response catResp = client.send(new Request(MessageProtocol.ACTION_GET_CATEGORIES, new JSONObject(), client.getSessionToken()));
                if (catResp.isSuccess()) {
                    applyCategories(catResp.getPayload());
                }

                Response prodResp = client.send(new Request(MessageProtocol.ACTION_GET_PRODUCTS, new JSONObject(), client.getSessionToken()));
                if (prodResp.isSuccess()) {
                    applyProducts(prodResp.getPayload());
                } else {
                    Platform.runLater(() -> displayProducts(List.of()));
                }
                return null;
            }
        };
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

    private void applyCategories(Object payload) {
        try {
            JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(String.valueOf(payload));

            Map<Integer, String> byId = new HashMap<>();
            Map<String, Integer> byName = new HashMap<>();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                int id = c.optInt("categoryId", c.optInt("category_id", 0));
                String name = c.optString("name", "");
                if (id <= 0 || name.isBlank()) continue;
                byId.put(id, name);
                byName.put(name, id);
            }

            Platform.runLater(() -> {
                categoryNameById.clear();
                categoryIdByName.clear();
                categoryNameById.putAll(byId);
                categoryIdByName.putAll(byName);

                selectedCategoryName = "Toutes";
                rebuildCategoryPills();
                filterProducts();
            });
        } catch (Exception ignored) {}
    }

    /**
     * Construit les pastilles « Toutes » + une par catégorie renvoyée par la BD.
     */
    private void rebuildCategoryPills() {
        if (categoryPills == null) {
            return;
        }
        categoryPills.getChildren().clear();

        ToggleGroup group = new ToggleGroup();

        ToggleButton allBtn = new ToggleButton("Toutes");
        allBtn.getStyleClass().addAll("btn-category-pill", "category-pill-toggle");
        allBtn.setToggleGroup(group);
        allBtn.setSelected(true);
        allBtn.selectedProperty().addListener((obs, prev, now) -> {
            if (Boolean.TRUE.equals(now)) {
                selectedCategoryName = "Toutes";
                filterProducts();
            }
        });

        List<String> sorted = new ArrayList<>(categoryIdByName.keySet());
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

        for (String catName : sorted) {
            ToggleButton pill = new ToggleButton(catName);
            pill.getStyleClass().addAll("btn-category-pill", "category-pill-toggle");
            pill.setToggleGroup(group);
            final String name = catName;
            pill.selectedProperty().addListener((obs, prev, now) -> {
                if (Boolean.TRUE.equals(now)) {
                    selectedCategoryName = name;
                    filterProducts();
                }
            });
            categoryPills.getChildren().add(pill);
        }

        categoryPills.getChildren().add(0, allBtn);
    }

    private void applyProducts(Object payload) {
        try {
            JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(String.valueOf(payload));
            List<Product> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
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
                    java.util.List<String> urls = new java.util.ArrayList<>();
                    for (int j = 0; j < images.length(); j++) {
                        String url = images.optString(j, "");
                        if (!url.isBlank()) urls.add(url);
                    }
                    pr.setImageUrls(urls);
                }
                list.add(pr);
            }
            Platform.runLater(() -> {
                allProducts = list;
                filterProducts();
            });
        } catch (Exception ignored) {}
    }

    /**
     * Rafraichit la grille des produits.
     */
    private void displayProducts(List<Product> products) {
        productsGrid.getChildren().clear();

        if (products.isEmpty()) {
            Label noResults = new Label("Aucun produit ne correspond à votre recherche.");
            noResults.getStyleClass().addAll("body-text", "empty-state");
            productsGrid.getChildren().add(noResults);
            return;
        }

        for (Product product : products) {
            productsGrid.getChildren().add(createProductCard(product));
        }
    }

    /**
     * Construit dynamiquement la "Carte Produit" pour l'UI.
     */
    private VBox createProductCard(Product p) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("product-card", "product-card--rich", "product-card-clickable");
        card.setPrefWidth(280);
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.setCursor(Cursor.HAND);

        card.setOnMouseClicked(e -> SceneManager.showProductDetail(p));

        // Header: catégorie + stock (pill)
        Label categoryLabel = new Label(getCategoryName(p.getCategoryId()));
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
        header.getStyleClass().add("product-card__header");

        // Media (image)
        StackPane media = new StackPane();
        media.getStyleClass().add("product-media");
        media.setPrefHeight(130);

        ImageView img = new ImageView();
        img.getStyleClass().add("product-image");
        img.setPreserveRatio(true);
        img.setFitWidth(280 - 48);
        img.setFitHeight(130);
        img.setSmooth(true);
        img.setCache(true);

        Label placeholder = new Label("Aucune image");
        placeholder.getStyleClass().add("image-placeholder");

        String url = p.getImageUrl();
        if (url != null) {
            url = url.trim();
        }
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
                // keep placeholder
            }
        }
        media.getChildren().addAll(img, placeholder);

        Label nameLabel = new Label(p.getName());
        nameLabel.getStyleClass().add("product-title");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(260);

        Label priceLabel = new Label(String.format("%.2f Dhs", p.getPrice()));
        priceLabel.getStyleClass().add("product-price");
        priceLabel.setMaxWidth(Double.MAX_VALUE);

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

    /**
     * Crops the image to fill the target box (like CSS background-size: cover).
     */
    private static void applyCoverViewport(ImageView view, Image image, double targetW, double targetH) {
        if (view == null || image == null) return;
        double iw = image.getWidth();
        double ih = image.getHeight();
        if (iw <= 0 || ih <= 0 || targetW <= 0 || targetH <= 0) return;

        double targetRatio = targetW / targetH;
        double imageRatio = iw / ih;

        double vw, vh, vx, vy;
        if (imageRatio > targetRatio) {
            // image is wider → crop left/right
            vh = ih;
            vw = ih * targetRatio;
            vx = (iw - vw) / 2.0;
            vy = 0;
        } else {
            // image is taller → crop top/bottom
            vw = iw;
            vh = iw / targetRatio;
            vx = 0;
            vy = (ih - vh) / 2.0;
        }
        view.setViewport(new Rectangle2D(vx, vy, vw, vh));
    }

    /**
     * Convertit l'ID Catégorie en Texte (mock simple).
     */
    private String getCategoryName(int categoryId) {
        return categoryNameById.getOrDefault(categoryId, "Autre");
    }

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;
        String lower = u.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return u;
        return new java.io.File(u).toURI().toString();
    }
}
