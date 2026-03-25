package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.OrderStatus;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.SceneManager;
import javafx.application.Platform;
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
 * KAN-9 — Admin interface (ADMIN only).
 * Tabs: Products CRUD, Orders list + status update, Users list + suspend.
 */
public class AdminController {

    // ── View models ──────────────────────────────────────────────────────────
    public static final class ProductRow {
        final IntegerProperty productId = new SimpleIntegerProperty();
        final IntegerProperty categoryId = new SimpleIntegerProperty();
        final StringProperty name = new SimpleStringProperty();
        final DoubleProperty price = new SimpleDoubleProperty();
        final IntegerProperty stock = new SimpleIntegerProperty();
        final StringProperty description = new SimpleStringProperty();
        final StringProperty imageUrl = new SimpleStringProperty();
    }

    public static final class OrderRow {
        final StringProperty orderId = new SimpleStringProperty();
        final IntegerProperty userId = new SimpleIntegerProperty();
        final DoubleProperty total = new SimpleDoubleProperty();
        final StringProperty status = new SimpleStringProperty();
    }

    public static final class UserRow {
        final IntegerProperty userId = new SimpleIntegerProperty();
        final StringProperty username = new SimpleStringProperty();
        final StringProperty email = new SimpleStringProperty();
        final StringProperty role = new SimpleStringProperty();
        final BooleanProperty suspended = new SimpleBooleanProperty();
    }

    // ── Category row view-model (KAN-18) ─────────────────────────────────────
    public static final class CategoryRow {
        final IntegerProperty categoryId  = new SimpleIntegerProperty();
        final StringProperty  name        = new SimpleStringProperty();
        final StringProperty  description = new SimpleStringProperty();
    }

    // ── FXML: Products ───────────────────────────────────────────────────────
    @FXML private TableView<ProductRow> productsTable;
    @FXML private TableColumn<ProductRow, Number> colProdId;
    @FXML private TableColumn<ProductRow, String> colProdName;
    @FXML private TableColumn<ProductRow, Number> colProdCategory;
    @FXML private TableColumn<ProductRow, String> colProdPrice;
    @FXML private TableColumn<ProductRow, Number> colProdStock;

    @FXML private TextField prodNameField;
    @FXML private TextField prodCategoryField;
    @FXML private TextField prodPriceField;
    @FXML private TextField prodStockField;
    @FXML private TextField prodImageField;
    @FXML private TextArea prodDescField;

    // ── FXML: Orders ─────────────────────────────────────────────────────────
    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, String> colOrderId;
    @FXML private TableColumn<OrderRow, Number> colOrderUser;
    @FXML private TableColumn<OrderRow, String> colOrderTotal;
    @FXML private TableColumn<OrderRow, String> colOrderStatus;
    @FXML private TableColumn<OrderRow, Void> colOrderAction;

    // ── FXML: Users ──────────────────────────────────────────────────────────
    @FXML private TableView<UserRow> usersTable;
    @FXML private TableColumn<UserRow, Number> colUserId;
    @FXML private TableColumn<UserRow, String> colUsername;
    @FXML private TableColumn<UserRow, String> colEmail;
    @FXML private TableColumn<UserRow, String> colRole;
    @FXML private TableColumn<UserRow, Boolean> colSuspended;
    @FXML private TableColumn<UserRow, Void> colUserAction;

    // ── FXML: Categories (KAN-18) ─────────────────────────────────────────────
    @FXML private TableView<CategoryRow> categoriesTable;
    @FXML private TableColumn<CategoryRow, Number> colCatId;
    @FXML private TableColumn<CategoryRow, String> colCatName;
    @FXML private TableColumn<CategoryRow, String> colCatDesc;
    @FXML private TextField catNameField;
    @FXML private TextField catDescField;

    @FXML private Label messageLabel;

    private final ObservableList<ProductRow>  productRows  = FXCollections.observableArrayList();
    private final ObservableList<OrderRow>    orderRows    = FXCollections.observableArrayList();
    private final ObservableList<UserRow>     userRows     = FXCollections.observableArrayList();
    private final ObservableList<CategoryRow> categoryRows = FXCollections.observableArrayList();

    private Integer editingProductId  = null;
    private Integer editingCategoryId = null;

    @FXML
    public void initialize() {
        // Guard ADMIN-only
        if (!ClientSession.getInstance().isAdmin()) {
            Platform.runLater(SceneManager::showHome);
            return;
        }

        // Restrict text inputs
        prodCategoryField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
        prodStockField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
        prodPriceField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*([\\.]\\d*)?") ? change : null));

        initProductsTable();
        initOrdersTable();
        initUsersTable();
        initCategoriesTable();

        refreshProducts();
        refreshOrders();
        refreshUsers();
        refreshCategories();
    }

    @FXML
    private void handleBackToHome() {
        SceneManager.showHome();
    }

    // ── Products tab ─────────────────────────────────────────────────────────
    private void initProductsTable() {
        productsTable.setItems(productRows);
        colProdId.setCellValueFactory(cd -> cd.getValue().productId);
        colProdName.setCellValueFactory(cd -> cd.getValue().name);
        colProdCategory.setCellValueFactory(cd -> cd.getValue().categoryId);
        colProdPrice.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%.2f Dhs", cd.getValue().price.get())));
        colProdStock.setCellValueFactory(cd -> cd.getValue().stock);

        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, row) -> {
            if (row == null) return;
            editingProductId = row.productId.get();
            prodNameField.setText(row.name.get());
            prodCategoryField.setText(String.valueOf(row.categoryId.get()));
            prodPriceField.setText(String.valueOf(row.price.get()));
            prodStockField.setText(String.valueOf(row.stock.get()));
            prodImageField.setText(row.imageUrl.get() == null ? "" : row.imageUrl.get());
            prodDescField.setText(row.description.get() == null ? "" : row.description.get());
        });
    }

    @FXML
    private void handleNewProduct() {
        editingProductId = null;
        prodNameField.clear();
        prodCategoryField.clear();
        prodPriceField.clear();
        prodStockField.clear();
        prodImageField.clear();
        prodDescField.clear();
        productsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSaveProduct() {
        setMessage(null, false);
        String name = prodNameField.getText().trim();
        int categoryId = parseInt(prodCategoryField.getText(), -1);
        double price = parseDouble(prodPriceField.getText(), -1);
        int stock = parseInt(prodStockField.getText(), -1);
        String imageUrl = prodImageField.getText().trim();
        String desc = prodDescField.getText().trim();

        if (name.isBlank() || categoryId <= 0 || price < 0 || stock < 0) {
            setMessage("Vérifie les champs: nom, categoryId>0, prix>=0, stock>=0.", true);
            return;
        }

        boolean isCreate = editingProductId == null;
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                if (!isCreate) payload.put("product_id", editingProductId);
                payload.put("category_id", categoryId);
                payload.put("name", name);
                payload.put("description", desc);
                payload.put("price", price);
                payload.put("stock", stock);
                payload.put("image_url", imageUrl.isBlank() ? JSONObject.NULL : imageUrl);

                String action = isCreate ? MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT : MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT;
                return client.send(new Request(action, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Enregistrement impossible." : r.getMessage(), true);
                return;
            }
            setMessage(isCreate ? "Produit créé." : "Produit mis à jour.", false);
            refreshProducts();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de l'enregistrement.", true));
        runTask(t);
    }

    @FXML
    private void handleDeleteProduct() {
        setMessage(null, false);
        ProductRow row = productsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            setMessage("Sélectionne un produit à supprimer.", true);
            return;
        }
        int productId = row.productId.get();
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("product_id", productId);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Suppression impossible." : r.getMessage(), true);
                return;
            }
            setMessage("Produit supprimé.", false);
            handleNewProduct();
            refreshProducts();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de la suppression.", true));
        runTask(t);
    }

    private void refreshProducts() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_GET_PRODUCTS, new JSONObject(), client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Impossible de charger les produits." : r.getMessage(), true);
                return;
            }
            productRows.setAll(parseProducts(r));
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors du chargement des produits.", true));
        runTask(t);
    }

    private ObservableList<ProductRow> parseProducts(Response r) {
        ObservableList<ProductRow> list = FXCollections.observableArrayList();
        Object payload = r.getPayload();
        JSONArray arr;
        if (payload instanceof JSONArray) arr = (JSONArray) payload;
        else arr = new JSONArray(payload.toString());

        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.getJSONObject(i);
            ProductRow row = new ProductRow();
            row.productId.set(p.optInt("productId", p.optInt("product_id", 0)));
            row.categoryId.set(p.optInt("categoryId", p.optInt("category_id", 0)));
            row.name.set(p.optString("name", ""));
            row.description.set(p.optString("description", ""));
            row.price.set(p.optDouble("price", 0.0));
            row.stock.set(p.optInt("stock", 0));
            row.imageUrl.set(p.optString("imageUrl", p.optString("image_url", "")));
            list.add(row);
        }
        return list;
    }

    // ── Orders tab ───────────────────────────────────────────────────────────
    private void initOrdersTable() {
        ordersTable.setItems(orderRows);
        colOrderId.setCellValueFactory(cd -> cd.getValue().orderId);
        colOrderUser.setCellValueFactory(cd -> cd.getValue().userId);
        colOrderTotal.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%.2f Dhs", cd.getValue().total.get())));
        colOrderStatus.setCellValueFactory(cd -> cd.getValue().status);

        colOrderAction.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> statusCombo = new ComboBox<>();
            private final Button apply = new Button("Appliquer");
            private final HBox box = new HBox(8, statusCombo, apply);
            {
                statusCombo.getItems().addAll(
                        OrderStatus.PENDING.name(),
                        OrderStatus.VALIDATED.name(),
                        OrderStatus.SHIPPED.name(),
                        OrderStatus.DELIVERED.name(),
                        OrderStatus.CANCELLED.name()
                );
                apply.getStyleClass().add("btn-outline");
                apply.setOnAction(e -> {
                    OrderRow row = getTableRow() != null ? (OrderRow) getTableRow().getItem() : null;
                    if (row == null) return;
                    String status = statusCombo.getValue();
                    if (status == null || status.isBlank()) return;
                    updateOrderStatus(row.orderId.get(), status);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                OrderRow row = (OrderRow) getTableRow().getItem();
                statusCombo.setValue(row.status.get());
                setGraphic(box);
            }
        });
    }

    @FXML
    private void handleRefreshOrders() {
        refreshOrders();
    }

    private void refreshOrders() {
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
                setMessage(r.getMessage().isBlank() ? "Impossible de charger les commandes." : r.getMessage(), true);
                return;
            }
            orderRows.setAll(parseOrders(r));
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors du chargement des commandes.", true));
        runTask(t);
    }

    private ObservableList<OrderRow> parseOrders(Response r) {
        ObservableList<OrderRow> list = FXCollections.observableArrayList();
        Object payload = r.getPayload();
        JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(payload.toString());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            OrderRow row = new OrderRow();
            row.orderId.set(o.optString("orderId", o.optString("order_id", "")));
            row.userId.set(o.optInt("userId", o.optInt("user_id", 0)));
            row.total.set(o.optDouble("totalAmount", o.optDouble("total_amount", 0.0)));
            row.status.set(o.optString("status", "PENDING"));
            list.add(row);
        }
        return list;
    }

    private void updateOrderStatus(String orderId, String newStatus) {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("order_id", orderId);
                payload.put("status", newStatus);
                return client.send(new Request(MessageProtocol.ACTION_UPDATE_ORDER_STATUS, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Statut non mis à jour." : r.getMessage(), true);
                return;
            }
            setMessage("Statut mis à jour.", false);
            refreshOrders();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de la mise à jour du statut.", true));
        runTask(t);
    }

    // ── Users tab ────────────────────────────────────────────────────────────
    private void initUsersTable() {
        usersTable.setItems(userRows);
        colUserId.setCellValueFactory(cd -> cd.getValue().userId);
        colUsername.setCellValueFactory(cd -> cd.getValue().username);
        colEmail.setCellValueFactory(cd -> cd.getValue().email);
        colRole.setCellValueFactory(cd -> cd.getValue().role);
        colSuspended.setCellValueFactory(cd -> cd.getValue().suspended);

        colUserAction.setCellFactory(tc -> new TableCell<>() {
            private final Button toggle = new Button("Suspendre");
            {
                toggle.getStyleClass().add("btn-outline");
                toggle.setOnAction(e -> {
                    UserRow row = getTableRow() != null ? (UserRow) getTableRow().getItem() : null;
                    if (row == null) return;
                    boolean target = !row.suspended.get();
                    setUserSuspended(row.userId.get(), target);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                UserRow row = (UserRow) getTableRow().getItem();
                toggle.setText(row.suspended.get() ? "Réactiver" : "Suspendre");
                setGraphic(new HBox(toggle));
            }
        });
    }

    @FXML
    private void handleRefreshUsers() {
        refreshUsers();
    }

    private void refreshUsers() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_LIST_USERS, new JSONObject(), client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Impossible de charger les utilisateurs." : r.getMessage(), true);
                return;
            }
            userRows.setAll(parseUsers(r));
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors du chargement des utilisateurs.", true));
        runTask(t);
    }

    private ObservableList<UserRow> parseUsers(Response r) {
        ObservableList<UserRow> list = FXCollections.observableArrayList();
        Object payload = r.getPayload();
        JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(payload.toString());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.getJSONObject(i);
            UserRow row = new UserRow();
            row.userId.set(u.optInt("userId", u.optInt("user_id", 0)));
            row.username.set(u.optString("username", ""));
            row.email.set(u.optString("email", ""));
            row.role.set(u.optString("role", User.Role.CLIENT.name()));
            row.suspended.set(u.optBoolean("suspended", u.optBoolean("is_suspended", false)));
            list.add(row);
        }
        return list;
    }

    private void setUserSuspended(int userId, boolean suspended) {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("suspended", suspended);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_SET_USER_SUSPENDED, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Action impossible." : r.getMessage(), true);
                return;
            }
            setMessage(suspended ? "Compte suspendu." : "Compte réactivé.", false);
            refreshUsers();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de l'action utilisateur.", true));
        runTask(t);
    }

    // ── Categories tab (KAN-18) ───────────────────────────────────────────────

    private void initCategoriesTable() {
        categoriesTable.setItems(categoryRows);
        colCatId.setCellValueFactory(cd -> cd.getValue().categoryId);
        colCatName.setCellValueFactory(cd -> cd.getValue().name);
        colCatDesc.setCellValueFactory(cd -> cd.getValue().description);

        categoriesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, row) -> {
            if (row == null) return;
            editingCategoryId = row.categoryId.get();
            catNameField.setText(row.name.get() == null ? "" : row.name.get());
            catDescField.setText(row.description.get() == null ? "" : row.description.get());
        });
    }

    @FXML
    private void handleRefreshCategories() {
        refreshCategories();
    }

    @FXML
    private void handleNewCategory() {
        editingCategoryId = null;
        catNameField.clear();
        catDescField.clear();
        categoriesTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSaveCategory() {
        setMessage(null, false);
        String name = catNameField.getText() == null ? "" : catNameField.getText().trim();
        String desc = catDescField.getText() == null ? "" : catDescField.getText().trim();

        if (name.isBlank()) {
            setMessage("Le nom de la catégorie est requis.", true);
            return;
        }

        boolean isCreate = editingCategoryId == null;
        final Integer catId = editingCategoryId;

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("name", name);
                if (!desc.isBlank()) payload.put("description", desc);
                String action;
                if (isCreate) {
                    action = MessageProtocol.ACTION_ADMIN_ADD_CATEGORY;
                } else {
                    payload.put("id", catId);
                    action = MessageProtocol.ACTION_ADMIN_UPDATE_CATEGORY;
                }
                return client.send(new Request(action, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Enregistrement impossible." : r.getMessage(), true);
                return;
            }
            setMessage(isCreate ? "Catégorie créée." : "Catégorie mise à jour.", false);
            handleNewCategory();
            refreshCategories();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de l'enregistrement.", true));
        runTask(t);
    }

    @FXML
    private void handleDeleteCategory() {
        setMessage(null, false);
        CategoryRow row = categoriesTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            setMessage("Sélectionne une catégorie à supprimer.", true);
            return;
        }
        int catId = row.categoryId.get();
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("id", catId);
                return client.send(new Request(
                        MessageProtocol.ACTION_ADMIN_DELETE_CATEGORY, payload, client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Suppression impossible (des produits sont liés ?)." : r.getMessage(), true);
                return;
            }
            setMessage("Catégorie supprimée.", false);
            handleNewCategory();
            refreshCategories();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de la suppression.", true));
        runTask(t);
    }

    private void refreshCategories() {
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(
                        MessageProtocol.ACTION_GET_CATEGORIES, new JSONObject(), client.getSessionToken()));
            }
        };
        t.setOnSucceeded(e -> {
            Response r = t.getValue();
            if (!r.isSuccess()) {
                setMessage(r.getMessage().isBlank() ? "Impossible de charger les catégories." : r.getMessage(), true);
                return;
            }
            categoryRows.setAll(parseCategories(r));
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors du chargement des catégories.", true));
        runTask(t);
    }

    private ObservableList<CategoryRow> parseCategories(Response r) {
        ObservableList<CategoryRow> list = FXCollections.observableArrayList();
        Object payload = r.getPayload();
        JSONArray arr = payload instanceof JSONArray ? (JSONArray) payload : new JSONArray(payload.toString());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            CategoryRow row = new CategoryRow();
            row.categoryId.set(c.optInt("categoryId", c.optInt("category_id", 0)));
            row.name.set(c.optString("name", ""));
            row.description.set(c.optString("description", ""));
            list.add(row);
        }
        return list;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
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

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }
}

