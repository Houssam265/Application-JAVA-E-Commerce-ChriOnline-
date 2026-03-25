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
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import org.json.JSONArray;
import org.json.JSONObject;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;

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
    @FXML private TextArea prodDescField;

    @FXML private Label prodImageFileNameLabel;
    @FXML private ImageView prodImagePreview;
    @FXML private Label prodImagePreviewPlaceholderLabel;

    // ── FXML: Orders ─────────────────────────────────────────────────────────
    @FXML private TextField ordersSearchField;
    @FXML private ComboBox<String> ordersStatusFilter;

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

    @FXML private TextField usersSearchField;
    @FXML private ComboBox<String> usersRoleFilter;
    @FXML private ComboBox<String> usersSuspendedFilter;

    @FXML private Label messageLabel;

    private final ObservableList<ProductRow> productRows = FXCollections.observableArrayList();
    private final ObservableList<OrderRow> orderRows = FXCollections.observableArrayList();
    private final ObservableList<UserRow> userRows = FXCollections.observableArrayList();

    private Integer editingProductId = null;
    private String editingImageUrl = null;
    private File selectedProductImageFile = null;
    private String selectedProductImageBase64 = null;

    private FilteredList<OrderRow> filteredOrders;
    private FilteredList<UserRow> filteredUsers;

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

        initOrdersFilters();
        initUsersFilters();

        refreshProducts();
        refreshOrders();
        refreshUsers();
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
            // Keep the existing image URL unless the admin uploads a new file.
            editingImageUrl = row.imageUrl.get();
            selectedProductImageFile = null;
            selectedProductImageBase64 = null;

            updateProductImagePreview(editingImageUrl);
            updateProductImageFileNameLabel(editingImageUrl);
            prodDescField.setText(row.description.get() == null ? "" : row.description.get());
        });
    }

    @FXML
    private void handleNewProduct() {
        editingProductId = null;
        editingImageUrl = null;
        selectedProductImageFile = null;
        selectedProductImageBase64 = null;
        prodNameField.clear();
        prodCategoryField.clear();
        prodPriceField.clear();
        prodStockField.clear();
        prodDescField.clear();
        if (prodImagePreview != null) prodImagePreview.setImage(null);
        if (prodImagePreviewPlaceholderLabel != null) {
            prodImagePreviewPlaceholderLabel.setVisible(true);
            prodImagePreviewPlaceholderLabel.setManaged(true);
        }
        if (prodImageFileNameLabel != null) prodImageFileNameLabel.setText("Aucune sélection");
        productsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleChooseProductImage() {
        Window window = null;
        if (productsTable != null && productsTable.getScene() != null) {
            window = productsTable.getScene().getWindow();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Uploader une image produit");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );

        File file = chooser.showOpenDialog(window);
        if (file == null) return;

        // Keep the payload reasonable (TCP line-based JSON protocol).
        long maxBytes = 1_500_000; // ~1.5MB
        if (file.length() > maxBytes) {
            setMessage("Image trop volumineuse (max ~1.5MB).", true);
            return;
        }

        final String[] base64Ref = new String[1];
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                byte[] bytes = Files.readAllBytes(file.toPath());
                base64Ref[0] = Base64.getEncoder().encodeToString(bytes);
                return null;
            }
        };

        t.setOnSucceeded(e -> {
            selectedProductImageFile = file;
            selectedProductImageBase64 = base64Ref[0];
            if (prodImageFileNameLabel != null) prodImageFileNameLabel.setText(file.getName());
            if (prodImagePreview != null) {
                prodImagePreview.setImage(new Image(file.toURI().toString(), true));
            }
            if (prodImagePreviewPlaceholderLabel != null) {
                prodImagePreviewPlaceholderLabel.setVisible(false);
                prodImagePreviewPlaceholderLabel.setManaged(false);
            }
        });
        t.setOnFailed(e -> setMessage("Erreur lors du chargement de l'image.", true));

        runTask(t);
    }

    @FXML
    private void handleSaveProduct() {
        setMessage(null, false);
        String name = prodNameField.getText().trim();
        int categoryId = parseInt(prodCategoryField.getText(), -1);
        double price = parseDouble(prodPriceField.getText(), -1);
        int stock = parseInt(prodStockField.getText(), -1);
        String desc = prodDescField.getText().trim();

        if (name.isBlank() || categoryId <= 0 || price < 0 || stock < 0) {
            setMessage("Vérifie les champs: nom, categoryId>0, prix>=0, stock>=0.", true);
            return;
        }

        boolean isCreate = editingProductId == null;
        final Integer editingProductIdSnapshot = editingProductId;
        final String editingImageUrlSnapshot = editingImageUrl;
        final String imageBase64Snapshot = selectedProductImageBase64;
        final String imageFilenameSnapshot =
                selectedProductImageFile != null ? selectedProductImageFile.getName() : "image";
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                if (!isCreate) payload.put("product_id", editingProductIdSnapshot);
                payload.put("category_id", categoryId);
                payload.put("name", name);
                payload.put("description", desc);
                payload.put("price", price);
                payload.put("stock", stock);
                // Image upload: either send base64 (new file) or keep the current image_url (edit).
                if (imageBase64Snapshot != null) {
                    payload.put("image_base64", imageBase64Snapshot);
                    payload.put("image_filename", imageFilenameSnapshot);
                } else {
                    payload.put("image_url",
                            (editingImageUrlSnapshot == null || editingImageUrlSnapshot.isBlank()) ? JSONObject.NULL : editingImageUrlSnapshot);
                }

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
        filteredOrders = new FilteredList<>(orderRows);
        ordersTable.setItems(filteredOrders);

        colOrderId.setCellValueFactory(cd -> cd.getValue().orderId);
        colOrderUser.setCellValueFactory(cd -> cd.getValue().userId);
        colOrderTotal.setCellValueFactory(cd ->
                new SimpleStringProperty(String.format(Locale.US, "%.2f Dhs", cd.getValue().total.get()))
        );
        colOrderStatus.setCellValueFactory(cd -> cd.getValue().status);

        // Status badge (plus lisible)
        colOrderStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(orderStatusToFrench(status));
                badge.getStyleClass().addAll("order-status-pill", orderStatusToPillClass(status));
                setGraphic(badge);
                setText(null);
            }
        });

        // Action: dropdown de statut (mise à jour immédiate)
        colOrderAction.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<OrderStatus> statusCombo = new ComboBox<>();

            {
                statusCombo.getItems().addAll(OrderStatus.values());
                statusCombo.getStyleClass().add("combo-box-premium");
                statusCombo.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(OrderStatus item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(orderStatusToFrench(item.name()));
                        }
                    }
                });
                statusCombo.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(OrderStatus item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) setText(null);
                        else setText(orderStatusToFrench(item.name()));
                    }
                });

                statusCombo.valueProperty().addListener((obs, oldV, newV) -> {
                    OrderRow row = getTableRow() != null ? (OrderRow) getTableRow().getItem() : null;
                    if (row == null || newV == null) return;
                    if (newV.name().equals(row.status.get())) return; // avoid re-trigger
                    updateOrderStatus(row, newV.name());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                OrderRow row = (OrderRow) getTableRow().getItem();
                try {
                    OrderStatus st = OrderStatus.valueOf(row.status.get());
                    statusCombo.setValue(st);
                    statusCombo.setDisable(st == OrderStatus.CANCELLED);
                    setGraphic(statusCombo);
                } catch (Exception ignored) {
                    setGraphic(statusCombo);
                }
            }
        });
    }

    private void initOrdersFilters() {
        if (ordersSearchField == null || ordersStatusFilter == null) return;

        ordersStatusFilter.getItems().add("TOUS");
        for (OrderStatus st : OrderStatus.values()) {
            ordersStatusFilter.getItems().add(st.name());
        }
        ordersStatusFilter.getSelectionModel().select("TOUS");

        ordersSearchField.textProperty().addListener((obs, oldV, n) -> applyOrdersFiltering());
        ordersStatusFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, n) -> applyOrdersFiltering());

        applyOrdersFiltering();
    }

    private void applyOrdersFiltering() {
        if (filteredOrders == null) return;

        String search = ordersSearchField != null ? ordersSearchField.getText() : null;
        String status = ordersStatusFilter != null ? ordersStatusFilter.getSelectionModel().getSelectedItem() : null;

        String statusFilter = (status == null || "TOUS".equals(status)) ? null : status;
        String searchNorm = search == null ? "" : search.trim().toLowerCase(Locale.US);

        filteredOrders.setPredicate(row -> {
            boolean matchesSearch = searchNorm.isEmpty() ||
                    (row.orderId.get() != null && row.orderId.get().toLowerCase(Locale.US).contains(searchNorm));
            boolean matchesStatus = statusFilter == null || statusFilter.equals(row.status.get());
            return matchesSearch && matchesStatus;
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

    private void updateOrderStatus(OrderRow rowRef, String newStatus) {
        if (rowRef == null) return;
        String orderId = rowRef.orderId.get();
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
            rowRef.status.set(newStatus);
            setMessage("Statut mis à jour.", false);
            applyOrdersFiltering();
            ordersTable.refresh();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de la mise à jour du statut.", true));
        runTask(t);
    }

    // ── UI helpers (admin: products images, orders statuses) ────────────────
    private void updateProductImagePreview(String imageUrl) {
        if (prodImagePreview == null || prodImagePreviewPlaceholderLabel == null) return;

        String normalized = normalizeImageUrlForLocalFiles(imageUrl);
        if (normalized == null) {
            prodImagePreview.setImage(null);
            prodImagePreviewPlaceholderLabel.setVisible(true);
            prodImagePreviewPlaceholderLabel.setManaged(true);
            return;
        }

        try {
            prodImagePreview.setImage(new Image(normalized, true));
            prodImagePreviewPlaceholderLabel.setVisible(false);
            prodImagePreviewPlaceholderLabel.setManaged(false);
        } catch (Exception ex) {
            prodImagePreview.setImage(null);
            prodImagePreviewPlaceholderLabel.setVisible(true);
            prodImagePreviewPlaceholderLabel.setManaged(true);
        }
    }

    private void updateProductImageFileNameLabel(String imageUrl) {
        if (prodImageFileNameLabel == null) return;
        if (imageUrl == null || imageUrl.trim().isBlank() || "null".equalsIgnoreCase(imageUrl.trim())) {
            prodImageFileNameLabel.setText("Aucune image");
            return;
        }
        String u = imageUrl.trim();
        int idx = Math.max(u.lastIndexOf('/'), u.lastIndexOf('\\'));
        String name = idx >= 0 ? u.substring(idx + 1) : u;
        prodImageFileNameLabel.setText(name);
    }

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;

        // Keep remote or file URI as-is.
        String lower = u.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) {
            return u;
        }

        // Treat it as a local filesystem path (absolute or relative).
        return new File(u).toURI().toString();
    }

    private static String orderStatusToFrench(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING" -> "En attente";
            case "VALIDATED" -> "Validée";
            case "SHIPPED" -> "Expédiée";
            case "DELIVERED" -> "Livrée";
            case "CANCELLED" -> "Annulée";
            default -> status;
        };
    }

    private static String orderStatusToPillClass(String status) {
        if (status == null) return "pill-pending";
        return switch (status) {
            case "PENDING" -> "pill-pending";
            case "VALIDATED" -> "pill-validated";
            case "SHIPPED" -> "pill-shipped";
            case "DELIVERED" -> "pill-delivered";
            case "CANCELLED" -> "pill-cancelled";
            default -> "pill-pending";
        };
    }

    // ── Users tab ────────────────────────────────────────────────────────────
    private void initUsersTable() {
        filteredUsers = new FilteredList<>(userRows);
        usersTable.setItems(filteredUsers);
        colUserId.setCellValueFactory(cd -> cd.getValue().userId);
        colUsername.setCellValueFactory(cd -> cd.getValue().username);
        colEmail.setCellValueFactory(cd -> cd.getValue().email);
        colRole.setCellValueFactory(cd -> cd.getValue().role);
        colSuspended.setCellValueFactory(cd -> cd.getValue().suspended);

        // Role badge
        colRole.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null || role.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label badge = new Label(role);
                String cls = "role-pill-client";
                if ("ADMIN".equalsIgnoreCase(role)) cls = "role-pill-admin";
                badge.getStyleClass().add(cls);
                setGraphic(badge);
                setText(null);
            }
        });

        // Suspended badge
        colSuspended.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Boolean suspended, boolean empty) {
                super.updateItem(suspended, empty);
                if (empty || suspended == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                boolean isSusp = suspended;
                Label badge = new Label(isSusp ? "Oui" : "Non");
                badge.getStyleClass().add(isSusp ? "suspended-pill-yes" : "suspended-pill-no");
                setGraphic(badge);
                setText(null);
            }
        });

        colUserAction.setCellFactory(tc -> new TableCell<>() {
            private final Button toggle = new Button("Suspendre");
            {
                toggle.getStyleClass().add("btn-outline");
                toggle.setOnAction(e -> {
                    UserRow row = getTableRow() != null ? (UserRow) getTableRow().getItem() : null;
                    if (row == null) return;
                    boolean target = !row.suspended.get();
                    setUserSuspended(row, target);
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

    private void initUsersFilters() {
        if (usersSearchField == null || usersRoleFilter == null || usersSuspendedFilter == null) return;

        usersRoleFilter.getItems().add("TOUS");
        usersRoleFilter.getItems().add("CLIENT");
        usersRoleFilter.getItems().add("ADMIN");
        usersRoleFilter.getSelectionModel().select("TOUS");

        usersSuspendedFilter.getItems().add("TOUS");
        usersSuspendedFilter.getItems().add("SUSPENDED");
        usersSuspendedFilter.getItems().add("ACTIVE");
        usersSuspendedFilter.getSelectionModel().select("TOUS");

        usersSearchField.textProperty().addListener((obs, oldV, n) -> applyUsersFiltering());
        usersRoleFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, n) -> applyUsersFiltering());
        usersSuspendedFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, n) -> applyUsersFiltering());

        applyUsersFiltering();
    }

    private void applyUsersFiltering() {
        if (filteredUsers == null) return;

        String search = usersSearchField != null ? usersSearchField.getText() : null;
        String roleSel = usersRoleFilter != null ? usersRoleFilter.getSelectionModel().getSelectedItem() : null;
        String suspSel = usersSuspendedFilter != null ? usersSuspendedFilter.getSelectionModel().getSelectedItem() : null;

        String roleFilter = (roleSel == null || "TOUS".equalsIgnoreCase(roleSel)) ? null : roleSel;
        Boolean suspendedFilter = null;
        if (suspSel != null && !"TOUS".equalsIgnoreCase(suspSel)) {
            suspendedFilter = "SUSPENDED".equalsIgnoreCase(suspSel);
        }

        String searchNorm = search == null ? "" : search.trim().toLowerCase(Locale.US);
        final String roleFilterFinal = roleFilter;
        final Boolean suspendedFilterFinal = suspendedFilter;

        filteredUsers.setPredicate(row -> {
            boolean matchesSearch = searchNorm.isEmpty()
                    || (row.username.get() != null && row.username.get().toLowerCase(Locale.US).contains(searchNorm))
                    || (row.email.get() != null && row.email.get().toLowerCase(Locale.US).contains(searchNorm))
                    || String.valueOf(row.userId.get()).contains(searchNorm);

            boolean matchesRole = roleFilterFinal == null || (row.role.get() != null && row.role.get().equalsIgnoreCase(roleFilterFinal));
            boolean matchesSusp = suspendedFilterFinal == null || row.suspended.get() == suspendedFilterFinal;
            return matchesSearch && matchesRole && matchesSusp;
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

    private void setUserSuspended(UserRow rowRef, boolean suspended) {
        if (rowRef == null) return;
        int userId = rowRef.userId.get();
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
            rowRef.suspended.set(suspended);
            setMessage(suspended ? "Compte suspendu." : "Compte réactivé.", false);
            applyUsersFiltering();
            usersTable.refresh();
        });
        t.setOnFailed(e -> setMessage("Erreur réseau lors de l'action utilisateur.", true));
        runTask(t);
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

