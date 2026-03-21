package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

    private final ObservableList<CartRow> rows = FXCollections.observableArrayList();

    /** TVA affichée (0 % par défaut ; modifier si besoin). */
    private static final double TAX_RATE = 0.0;

    @FXML
    public void initialize() {
        rows.addListener((javafx.collections.ListChangeListener<CartRow>) c -> rebuildCartItems());
        rebuildCartItems();
        refreshCart();
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
                Image im = new Image(url, true);
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
                setMessage(r.getMessage().isBlank() ? "Impossible de vider le panier." : r.getMessage(), true);
                return;
            }
            rows.clear();
            updateTotals();
            setMessage("Panier vidé.", false);
        });
        t.setOnFailed(ev -> setMessage("Erreur réseau lors du vidage du panier.", true));
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
                setMessage(r.getMessage().isBlank() ? "Commande échouée." : r.getMessage(), true);
                return;
            }
            setMessage("Commande validée avec succès.", false);
            rows.clear();
            updateTotals();
        });
        t.setOnFailed(e -> {
            checkoutButton.setDisable(false);
            setMessage("Erreur réseau lors de la validation.", true);
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
                setMessage(r.getMessage().isBlank() ? "Impossible de charger le panier." : r.getMessage(), true);
                return;
            }
            rows.setAll(parseCartRows(r));
            updateTotals();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors du chargement du panier.", true));
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
                setMessage(r.getMessage().isBlank() ? "Suppression impossible." : r.getMessage(), true);
                return;
            }
            rows.removeIf(x -> x.getCartItemId() == cartItemId);
            updateTotals();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de la suppression.", true));
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
                setMessage(r.getMessage().isBlank() ? "Quantité non mise à jour côté serveur." : r.getMessage(), true);
                refreshCart();
            }
        });
        t.setOnFailed(e -> {
            setMessage("Erreur réseau lors de la mise à jour de quantité.", true);
            refreshCart();
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
