package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.ImageCache;
import com.chrionline.ui.SceneManager;
import com.chrionline.ui.notifications.AppNotification;
import com.chrionline.ui.notifications.NotificationCenter;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONObject;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductDetailController {

    @FXML private Label productNameLabel;
    @FXML private Label productCategoryLabel;
    @FXML private Label productPriceLabel;
    @FXML private Label productDescriptionLabel;
    @FXML private Label stockStatusLabel;
    @FXML private Label availabilityCaptionLabel;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button addToCartButton;
    @FXML private Label messageLabel;
    @FXML private ImageView productImageView;
    @FXML private VBox imagePlaceholderBox;
    @FXML private Label imagePlaceholderLabel;
    @FXML private FlowPane galleryPane;
    @FXML private Button prevImageButton;
    @FXML private Button nextImageButton;

    @FXML private Button navHomeBtn;
    @FXML private Button navCatalogueBtn;
    @FXML private TextField headerSearchField;
    @FXML private MenuButton accountMenuButton;
    @FXML private MenuItem accountProfileItem;
    @FXML private MenuItem accountOrdersItem;
    @FXML private MenuItem accountAdminItem;
    @FXML private MenuItem accountLogoutItem;
    @FXML private Button bellButton;
    @FXML private Label unreadBadge;
    @FXML private VBox toastLayer;
    @FXML private StackPane drawerScrim;
    @FXML private VBox drawerPanel;
    @FXML private ListView<AppNotification> notificationsList;

    private Product product;
    private String currentMainImageUrl;
    private List<String> currentImageUrls = new ArrayList<>();

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        configureAccountMenu(session);
        if (quantitySpinner != null) {
            quantitySpinner.getStyleClass().add("premium-spinner");
            quantitySpinner.setEditable(true);
        }
        bindNotifications();
    }

    public void setProduct(Product product) {
        this.product = product;
        productNameLabel.setText(product.getName());
        productPriceLabel.setText(String.format("%.2f Dhs", product.getPrice()));

        String desc = product.getDescription();
        productDescriptionLabel.setText((desc != null && !desc.isEmpty()) ? desc : "Aucune description disponible pour ce produit.");

        productCategoryLabel.setText(getCategoryName(product.getCategoryId()));
        renderGallery(resolveProductImages(product));
        loadFullProductDetails();

        if (product.isAvailable() && product.getStock() > 0) {
            stockStatusLabel.setText("En stock");
            stockStatusLabel.getStyleClass().setAll("badge-instock");
            availabilityCaptionLabel.setText(product.getStock() + " en stock");

            SpinnerValueFactory<Integer> valueFactory =
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, product.getStock(), 1);
            quantitySpinner.setValueFactory(valueFactory);

            quantitySpinner.setDisable(false);
            addToCartButton.setDisable(false);
            setMessage(null, false);
        } else {
            stockStatusLabel.setText("Indisponible");
            stockStatusLabel.getStyleClass().setAll("badge-outstock");
            availabilityCaptionLabel.setText("Rupture de stock");

            SpinnerValueFactory<Integer> valueFactory =
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 0, 0);
            quantitySpinner.setValueFactory(valueFactory);

            quantitySpinner.setDisable(true);
            addToCartButton.setDisable(true);
            setMessage("Ce produit est actuellement en rupture de stock.", true);
        }
    }

    @FXML
    private void handleAddToCart() {
        if (product == null || !product.isAvailable() || product.getStock() <= 0) {
            return;
        }
        int quantity = quantitySpinner.getValue();
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("product_id", product.getProductId());
                payload.put("quantity", quantity);
                return client.send(new Request(MessageProtocol.ACTION_ADD_TO_CART, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                String msg = r.getMessage();
                setMessage(msg == null || msg.isBlank() ? "Impossible d'ajouter au panier." : msg, true);
                return;
            }
            setMessage("Ajoute au panier (" + quantity + ") !", false);
        });
        t.setOnFailed(e -> setMessage("Erreur reseau lors de l'ajout au panier.", true));
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleBackToCatalog() {
        SceneManager.showCatalogue();
    }

    @FXML
    private void handleOpenHome() {
        SceneManager.showHome();
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
    private void handleOpenProfile() {
        SceneManager.showProfile();
    }

    @FXML
    private void handleOpenOrders() {
        SceneManager.showOrderHistory();
    }

    @FXML
    private void handleOpenAdmin() {
        SceneManager.showAdmin();
    }

    private void configureAccountMenu(ClientSession session) {
        if (accountMenuButton == null) return;
        String username = session.getUsername() == null || session.getUsername().isBlank()
                ? "Utilisateur"
                : session.getUsername();
        accountMenuButton.setText("Bonjour, " + username);

        if (accountProfileItem != null) {
            accountProfileItem.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-user"));
            accountProfileItem.setOnAction(e -> handleOpenProfile());
        }
        if (accountOrdersItem != null) {
            accountOrdersItem.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-box-open"));
            accountOrdersItem.setOnAction(e -> handleOpenOrders());
        }
        if (accountAdminItem != null) {
            accountAdminItem.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-user-shield"));
            accountAdminItem.setOnAction(e -> handleOpenAdmin());
            boolean isAdmin = session.isAdmin();
            accountAdminItem.setVisible(isAdmin);
            accountAdminItem.setDisable(!isAdmin);
        }
        if (accountLogoutItem != null) {
            accountLogoutItem.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-sign-out-alt"));
            accountLogoutItem.setOnAction(e -> handleLogout());
        }
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
            Client.getInstance().disconnect();
            ClientSession.getInstance().clear();
            SceneManager.showLogin();
        });
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

    private void bindNotifications() {
        NotificationCenter nc = NotificationCenter.getInstance();
        nc.unreadCountProperty().addListener((obs, ov, nv) -> updateUnreadBadge(nv == null ? 0 : nv.intValue()));
        updateUnreadBadge(nc.unreadCountProperty().get());

        if (notificationsList != null) {
            notificationsList.setFixedCellSize(-1);
            notificationsList.setCellFactory(lv -> new ListCell<>() {
                {
                    setOnMouseClicked(e -> {
                        AppNotification n = getItem();
                        if (n != null && !n.isRead()) {
                            NotificationCenter.getInstance().markAsRead(n);
                            updateItem(n, false);
                            notificationsList.refresh();
                        }
                    });
                    setPrefWidth(0);
                    setStyle("-fx-cursor: hand; -fx-padding: 8px 12px;");
                }

                @Override
                protected void updateItem(AppNotification item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("-fx-padding: 0;");
                        return;
                    }

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.TOP_LEFT);

                    Circle dot = new Circle(5);
                    dot.setFill(item.isRead() ? Color.TRANSPARENT : Color.web("#f46a3d"));

                    VBox dotBox = new VBox(dot);
                    dotBox.setAlignment(Pos.TOP_CENTER);
                    dotBox.setStyle("-fx-padding: 3 0 0 0;");

                    VBox textBox = new VBox(2);
                    textBox.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(textBox, Priority.ALWAYS);
                    row.setMaxWidth(Double.MAX_VALUE);

                    String hhmm = item.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));
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

        nc.getNotifications().addListener((ListChangeListener<AppNotification>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (AppNotification n : c.getAddedSubList()) {
                        showToast("Notification", n.getMessage());
                    }
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
        new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(drawerPanel.translateXProperty(), 0, Interpolator.EASE_OUT))).play();
    }

    @FXML
    private void closeNotifications() {
        if (drawerPanel == null || drawerScrim == null) return;
        Timeline t = new Timeline(new KeyFrame(Duration.millis(180),
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

    private void loadFullProductDetails() {
        if (product == null || product.getProductId() <= 0) return;
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("product_id", product.getProductId());
                return client.send(new Request(MessageProtocol.ACTION_GET_PRODUCT, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) return;
            try {
                JSONObject payload = r.getPayloadAsJsonObject();
                JSONObject p = payload.optJSONObject("product");
                if (p == null) return;
                List<String> urls = new ArrayList<>();
                org.json.JSONArray images = p.optJSONArray("imageUrls");
                if (images == null) images = p.optJSONArray("image_urls");
                if (images != null) {
                    for (int i = 0; i < images.length(); i++) {
                        String url = images.optString(i, "");
                        if (!url.isBlank()) urls.add(url);
                    }
                }
                if (urls.isEmpty()) {
                    String single = p.optString("imageUrl", p.optString("image_url", ""));
                    if (!single.isBlank()) urls.add(single);
                }
                if (!urls.isEmpty()) {
                    product.setImageUrls(urls);
                    product.setImageUrl(urls.get(0));
                    renderGallery(urls);
                }
            } catch (Exception ignored) {
            }
        });
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

    private List<String> resolveProductImages(Product product) {
        List<String> urls = product.getImageUrls();
        if (urls != null && !urls.isEmpty()) {
            return urls;
        }
        List<String> single = new ArrayList<>();
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            single.add(product.getImageUrl());
        }
        return single;
    }

    private void renderGallery(List<String> urls) {
        if (productImageView == null || imagePlaceholderLabel == null) return;
        if (urls == null || urls.isEmpty()) {
            currentImageUrls = new ArrayList<>();
            currentMainImageUrl = null;
            setMainImage(null);
            if (galleryPane != null) galleryPane.getChildren().clear();
            updateImageNavigationState();
            return;
        }

        currentImageUrls = new ArrayList<>(urls);
        String nextMain = currentMainImageUrl;
        if (nextMain == null || nextMain.isBlank() || !urls.contains(nextMain)) {
            nextMain = urls.get(0);
        }
        currentMainImageUrl = nextMain;
        setMainImage(nextMain);
        updateImageNavigationState();

        if (galleryPane == null) return;
        galleryPane.getChildren().clear();
        for (String url : urls) {
            boolean selected = url != null && url.equals(currentMainImageUrl);
            StackPane thumbWrap = new StackPane();
            thumbWrap.setMinSize(88, 88);
            thumbWrap.setMaxSize(88, 88);
            thumbWrap.setStyle(selected
                    ? "-fx-background-color: #ffffff; -fx-background-radius: 14px; -fx-border-color: #f46a3d; -fx-border-radius: 14px; -fx-border-width: 2px; -fx-padding: 4px;"
                    : "-fx-background-color: #ffffff; -fx-background-radius: 14px; -fx-border-color: rgba(148,163,184,0.35); -fx-border-radius: 14px; -fx-border-width: 1px; -fx-padding: 4px;");

            ImageView thumb = new ImageView();
            thumb.setFitWidth(78);
            thumb.setFitHeight(78);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);
            Rectangle clip = new Rectangle(78, 78);
            clip.setArcWidth(14);
            clip.setArcHeight(14);
            thumb.setClip(clip);
            try {
                String normalized = normalizeImageUrlForLocalFiles(url);
                if (normalized != null) {
                    thumb.setImage(ImageCache.get(normalized));
                }
            } catch (Exception ignored) {
            }
            thumbWrap.getChildren().add(thumb);
            thumbWrap.setOnMouseClicked(ev -> {
                currentMainImageUrl = url;
                setMainImage(url);
                renderGallery(urls);
            });
            galleryPane.getChildren().add(thumbWrap);
        }
    }

    @FXML
    private void handleShowPreviousImage() {
        if (currentImageUrls == null || currentImageUrls.size() <= 1 || currentMainImageUrl == null) return;
        int currentIndex = currentImageUrls.indexOf(currentMainImageUrl);
        if (currentIndex < 0) currentIndex = 0;
        int previousIndex = (currentIndex - 1 + currentImageUrls.size()) % currentImageUrls.size();
        currentMainImageUrl = currentImageUrls.get(previousIndex);
        setMainImage(currentMainImageUrl);
        renderGallery(currentImageUrls);
    }

    @FXML
    private void handleShowNextImage() {
        if (currentImageUrls == null || currentImageUrls.size() <= 1 || currentMainImageUrl == null) return;
        int currentIndex = currentImageUrls.indexOf(currentMainImageUrl);
        if (currentIndex < 0) currentIndex = 0;
        int nextIndex = (currentIndex + 1) % currentImageUrls.size();
        currentMainImageUrl = currentImageUrls.get(nextIndex);
        setMainImage(currentMainImageUrl);
        renderGallery(currentImageUrls);
    }

    private void updateImageNavigationState() {
        boolean enabled = currentImageUrls != null && currentImageUrls.size() > 1;
        if (prevImageButton != null) {
            prevImageButton.setDisable(!enabled);
            prevImageButton.setVisible(enabled);
            prevImageButton.setManaged(enabled);
        }
        if (nextImageButton != null) {
            nextImageButton.setDisable(!enabled);
            nextImageButton.setVisible(enabled);
            nextImageButton.setManaged(enabled);
        }
    }

    private void setMainImage(String url) {
        if (url != null) url = url.trim();
        boolean hasUrl = url != null && !url.isBlank() && !"null".equalsIgnoreCase(url);
        if (!hasUrl) {
            productImageView.setImage(null);
            if (imagePlaceholderBox != null) {
                imagePlaceholderBox.setVisible(true);
                imagePlaceholderBox.setManaged(true);
            }
            return;
        }
        try {
            String normalized = normalizeImageUrlForLocalFiles(url);
            Image image = ImageCache.get(normalized);
            productImageView.setImage(image);
            image.progressProperty().addListener((obs, ov, nv) -> {
                if (nv != null && nv.doubleValue() >= 1.0) {
                    Platform.runLater(() ->
                            applyCoverViewport(productImageView, image, productImageView.getFitWidth(), productImageView.getFitHeight()));
                }
            });
            if (imagePlaceholderBox != null) {
                imagePlaceholderBox.setVisible(false);
                imagePlaceholderBox.setManaged(false);
            }
        } catch (Exception ignored) {
            productImageView.setImage(null);
            if (imagePlaceholderBox != null) {
                imagePlaceholderBox.setVisible(true);
                imagePlaceholderBox.setManaged(true);
            }
        }
    }

    private String getCategoryName(int categoryId) {
        return switch (categoryId) {
            case 1 -> "PC Portables";
            case 2 -> "Smartphones";
            case 3 -> "Accessoires";
            default -> "Autre";
        };
    }

    private void setMessage(String text, boolean error) {
        if (messageLabel == null) return;
        if (text == null || text.isBlank()) {
            messageLabel.setText("");
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
            return;
        }
        messageLabel.setText(text);
        messageLabel.getStyleClass().setAll(error ? "global-error" : "success-label");
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void showToast(String title, String body) {
        if (toastLayer == null) return;
        VBox card = new VBox(4);
        card.setMaxWidth(320);
        card.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 14px; -fx-padding: 12px 14px; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.28), 18, 0.2, 0, 8);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 800;");

        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.86); -fx-font-size: 12px; -fx-font-weight: 600;");

        card.getChildren().addAll(titleLabel, bodyLabel);
        card.setOpacity(0);
        card.setTranslateY(8);
        toastLayer.getChildren().add(card);

        Timeline show = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.translateYProperty(), 8)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT))
        );

        Timeline hide = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), 1),
                        new KeyValue(card.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(card.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(card.translateYProperty(), -6, Interpolator.EASE_IN))
        );
        hide.setDelay(Duration.seconds(3.2));
        hide.setOnFinished(e -> toastLayer.getChildren().remove(card));

        show.play();
        hide.play();
    }

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;
        String lower = u.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return u;
        return new java.io.File(u).toURI().toString();
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
}
