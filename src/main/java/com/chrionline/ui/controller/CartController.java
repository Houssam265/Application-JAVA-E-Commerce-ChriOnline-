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
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * KAN-28 — Cart screen (Panier).
 * Features: list items, update quantity, remove item, totals, checkout.
 */
public class CartController {

    public static final class CartRow {
        private final IntegerProperty cartItemId = new SimpleIntegerProperty();
        private final IntegerProperty productId  = new SimpleIntegerProperty();
        private final StringProperty  name       = new SimpleStringProperty();
        private final DoubleProperty  unitPrice  = new SimpleDoubleProperty();
        private final IntegerProperty quantity   = new SimpleIntegerProperty();

        public int getCartItemId() { return cartItemId.get(); }
        public IntegerProperty cartItemIdProperty() { return cartItemId; }
        public int getProductId() { return productId.get(); }
        public IntegerProperty productIdProperty() { return productId; }
        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        public double getUnitPrice() { return unitPrice.get(); }
        public DoubleProperty unitPriceProperty() { return unitPrice; }
        public int getQuantity() { return quantity.get(); }
        public IntegerProperty quantityProperty() { return quantity; }

        public double getSubtotal() { return getUnitPrice() * getQuantity(); }
    }

    @FXML private TableView<CartRow> cartTable;
    @FXML private TableColumn<CartRow, String> colProduct;
    @FXML private TableColumn<CartRow, String> colUnitPrice;
    @FXML private TableColumn<CartRow, Number> colQuantity;
    @FXML private TableColumn<CartRow, String> colSubtotal;
    @FXML private TableColumn<CartRow, Void> colActions;

    @FXML private Label totalLabel;
    @FXML private Button checkoutButton;
    @FXML private Label messageLabel;

    private final ObservableList<CartRow> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        cartTable.setItems(rows);

        colProduct.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colUnitPrice.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%.2f Dhs", cd.getValue().getUnitPrice())));
        colSubtotal.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%.2f Dhs", cd.getValue().getSubtotal())));

        // Quantity column uses a Spinner per row
        colQuantity.setCellValueFactory(cd -> cd.getValue().quantityProperty());
        colQuantity.setCellFactory(tc -> new TableCell<>() {
            private final Spinner<Integer> spinner = new Spinner<>();

            {
                spinner.getStyleClass().add("premium-spinner");
                spinner.setEditable(true);
                spinner.valueProperty().addListener((obs, oldV, newV) -> {
                    CartRow row = getTableRow() != null ? (CartRow) getTableRow().getItem() : null;
                    if (row == null || newV == null) return;
                    if (newV <= 0) return;
                    if (newV == row.getQuantity()) return;
                    row.quantityProperty().set(newV);
                    updateTotals();
                    updateQuantityOnServer(row.getCartItemId(), newV);
                });
            }

            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                int current = ((CartRow) getTableRow().getItem()).getQuantity();
                SpinnerValueFactory<Integer> vf = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, current);
                spinner.setValueFactory(vf);
                setGraphic(spinner);
            }
        });

        // Actions column: remove button
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button remove = new Button("Suppr.");
            {
                remove.getStyleClass().add("btn-secondary");
                remove.setOnAction(e -> {
                    CartRow row = getTableRow() != null ? (CartRow) getTableRow().getItem() : null;
                    if (row == null) return;
                    removeItem(row.getCartItemId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(new HBox(remove));
            }
        });

        refreshCart();
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
        t.setOnFailed(e -> setMessage("Erreur réseau lors du vidage du panier.", true));
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
        double total = rows.stream().mapToDouble(CartRow::getSubtotal).sum();
        totalLabel.setText(String.format("%.2f Dhs", total));
        checkoutButton.setDisable(rows.isEmpty());
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

