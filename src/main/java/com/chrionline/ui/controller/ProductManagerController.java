package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Category;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * KAN-18 — Category Management + Product management screen (admin usage).
 *
 * Notes:
 * - All TCP calls are executed in a JavaFX Task
 * - UI updates go through Platform.runLater
 * - Buttons are disabled while a Task is running
 */
public class ProductManagerController {

    // ── View models ──────────────────────────────────────────────────────────
    public static final class ProductRow {
        final IntegerProperty productId = new SimpleIntegerProperty();
        final IntegerProperty categoryId = new SimpleIntegerProperty();
        final StringProperty name = new SimpleStringProperty();
        final DoubleProperty price = new SimpleDoubleProperty();
        final IntegerProperty stock = new SimpleIntegerProperty();
        final StringProperty description = new SimpleStringProperty();
        final BooleanProperty available = new SimpleBooleanProperty();
    }

    public static final class CategoryRow {
        final IntegerProperty categoryId = new SimpleIntegerProperty();
        final StringProperty name = new SimpleStringProperty();
        final StringProperty description = new SimpleStringProperty();
    }

    // ── FXML: Products list ──────────────────────────────────────────────────
    @FXML private TableView<ProductRow> productsTable;
    @FXML private TableColumn<ProductRow, String> colProdName;
    @FXML private TableColumn<ProductRow, String> colProdCategory;
    @FXML private TableColumn<ProductRow, String> colProdPrice;
    @FXML private TableColumn<ProductRow, Number> colProdStock;

    // ── FXML: Product form (ids required by spec) ────────────────────────────
    @FXML private TextField name;
    @FXML private TextField price;
    @FXML private TextArea description;
    @FXML private TextField stock;
    @FXML private ComboBox<CategoryRow> category;

    @FXML private Button addProductButton;
    @FXML private Button updateProductButton;
    @FXML private Button deleteProductButton;
    @FXML private Button clearProductButton;

    // ── FXML: Categories list/form ───────────────────────────────────────────
    @FXML private TableView<CategoryRow> categoriesTable;
    @FXML private TableColumn<CategoryRow, String> colCatName;
    @FXML private TableColumn<CategoryRow, String> colCatDescription;

    @FXML private TextField categoryNameField;
    @FXML private TextArea categoryDescriptionField;

    @FXML private Button addCategoryButton;
    @FXML private Button updateCategoryButton;
    @FXML private Button deleteCategoryButton;
    @FXML private Button clearCategoryButton;

    @FXML private Label statusLabel;

    private final ObservableList<ProductRow> productRows = FXCollections.observableArrayList();
    private final ObservableList<CategoryRow> categoryRows = FXCollections.observableArrayList();

    private Integer editingProductId = null;
    private Integer editingCategoryId = null;

    @FXML
    public void initialize() {
        initTables();
        initSelectionBindings();
        loadCategories();
        loadProducts();
    }

    private void initTables() {
        if (productsTable != null) {
            productsTable.setItems(productRows);
        }
        if (categoriesTable != null) {
            categoriesTable.setItems(categoryRows);
        }

        if (colProdName != null) colProdName.setCellValueFactory(cd -> cd.getValue().name);
        if (colProdPrice != null) colProdPrice.setCellValueFactory(cd ->
                new SimpleStringProperty(String.format("%.2f", cd.getValue().price.get())));
        if (colProdStock != null) colProdStock.setCellValueFactory(cd -> cd.getValue().stock);
        if (colProdCategory != null) colProdCategory.setCellValueFactory(cd ->
                new SimpleStringProperty(resolveCategoryName(cd.getValue().categoryId.get())));

        if (colCatName != null) colCatName.setCellValueFactory(cd -> cd.getValue().name);
        if (colCatDescription != null) colCatDescription.setCellValueFactory(cd -> cd.getValue().description);

        if (category != null) {
            category.setItems(categoryRows);
            category.setCellFactory(cb -> new ListCell<>() {
                @Override protected void updateItem(CategoryRow item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.name.get());
                }
            });
            category.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(CategoryRow item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.name.get());
                }
            });
        }
    }

    private void initSelectionBindings() {
        if (productsTable != null) {
            productsTable.getSelectionModel().selectedItemProperty().addListener((obs, ov, row) -> {
                updateProductToggleButton(row);
                if (row == null) return;
                editingProductId = row.productId.get();
                Platform.runLater(() -> {
                    name.setText(row.name.get());
                    price.setText(String.valueOf(row.price.get()));
                    stock.setText(String.valueOf(row.stock.get()));
                    description.setText(row.description.get() == null ? "" : row.description.get());
                    selectCategoryById(row.categoryId.get());
                });
            });
        }

        if (categoriesTable != null) {
            categoriesTable.getSelectionModel().selectedItemProperty().addListener((obs, ov, row) -> {
                if (row == null) return;
                editingCategoryId = row.categoryId.get();
                Platform.runLater(() -> {
                    categoryNameField.setText(row.name.get());
                    categoryDescriptionField.setText(row.description.get() == null ? "" : row.description.get());
                });
            });
        }
    }

    private String resolveCategoryName(int categoryId) {
        for (CategoryRow c : categoryRows) {
            if (c.categoryId.get() == categoryId) return c.name.get();
        }
        return String.valueOf(categoryId);
    }

    private void selectCategoryById(int categoryId) {
        if (category == null) return;
        for (CategoryRow c : categoryRows) {
            if (c.categoryId.get() == categoryId) {
                category.getSelectionModel().select(c);
                return;
            }
        }
        category.getSelectionModel().clearSelection();
    }

    // ── Loading (GET_CATEGORIES / GET_PRODUCTS) ──────────────────────────────
    private void loadCategories() {
        setStatus(null, false);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_GET_CATEGORIES, new JSONObject(), client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Unable to load categories.", true);
                return;
            }
            categoryRows.setAll(parseCategories(r.getPayload()));
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while loading categories.", true);
        });
        runTask(t);
    }

    private void loadProducts() {
        setStatus(null, false);
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                return client.send(new Request(MessageProtocol.ACTION_GET_PRODUCTS, new JSONObject(), client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Unable to load products.", true);
                return;
            }
            productRows.setAll(parseProducts(r.getPayload()));
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while loading products.", true);
        });
        runTask(t);
    }

    private ObservableList<CategoryRow> parseCategories(Object payload) {
        ObservableList<CategoryRow> list = FXCollections.observableArrayList();
        JSONArray arr = toJsonArray(payload);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            CategoryRow row = new CategoryRow();
            row.categoryId.set(c.optInt("categoryId", c.optInt("category_id", 0)));
            row.name.set(c.optString("name", ""));
            row.description.set(c.optString("description", ""));
            if (row.categoryId.get() > 0 && !row.name.get().isBlank()) {
                list.add(row);
            }
        }
        return list;
    }

    private ObservableList<ProductRow> parseProducts(Object payload) {
        ObservableList<ProductRow> list = FXCollections.observableArrayList();
        JSONArray arr = toJsonArray(payload);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.getJSONObject(i);
            ProductRow row = new ProductRow();
            row.productId.set(p.optInt("productId", p.optInt("product_id", 0)));
            row.categoryId.set(p.optInt("categoryId", p.optInt("category_id", 0)));
            row.name.set(p.optString("name", ""));
            row.description.set(p.optString("description", ""));
            row.price.set(p.optDouble("price", 0.0));
            row.stock.set(p.optInt("stock", 0));
            row.available.set(p.optBoolean("available", p.optBoolean("is_available", false)));
            list.add(row);
        }
        return list;
    }

    private static JSONArray toJsonArray(Object payload) {
        if (payload == null) return new JSONArray();
        if (payload instanceof JSONArray) return (JSONArray) payload;
        return new JSONArray(String.valueOf(payload));
    }

    // ── Product actions (ADD/UPDATE/ACTIVATE-DEACTIVATE_PRODUCT) ────────────
    @FXML
    private void handleAddProduct() {
        setStatus(null, false);
        Map<String, Object> parsed = parseProductForm();
        if (parsed == null) return;

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject(parsed);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT, payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Add product failed.", true);
                return;
            }
            setStatus("Product created.", false);
            handleClearProductSelection();
            loadProducts();
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while adding product.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleUpdateProduct() {
        setStatus(null, false);
        if (editingProductId == null) {
            setStatus("Select a product to update.", true);
            return;
        }
        Map<String, Object> parsed = parseProductForm();
        if (parsed == null) return;
        parsed.put("product_id", editingProductId);

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject(parsed);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT, payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Update product failed.", true);
                return;
            }
            setStatus("Product updated.", false);
            loadProducts();
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while updating product.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleDeleteProduct() {
        setStatus(null, false);
        ProductRow row = productsTable != null ? productsTable.getSelectionModel().getSelectedItem() : null;
        if (row == null) {
            setStatus("Select a product to activate or deactivate.", true);
            return;
        }
        int productId = row.productId.get();
        boolean activating = !row.available.get();

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("product_id", productId);
                return client.send(new Request(MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT, payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Product status update failed.", true);
                return;
            }
            setStatus(activating ? "Product activated." : "Product deactivated.", false);
            handleClearProductSelection();
            loadProducts();
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while updating product status.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleClearProductSelection() {
        editingProductId = null;
        if (productsTable != null) productsTable.getSelectionModel().clearSelection();
        if (name != null) name.clear();
        if (price != null) price.clear();
        if (stock != null) stock.clear();
        if (description != null) description.clear();
        if (category != null) category.getSelectionModel().clearSelection();
        updateProductToggleButton(null);
    }

    private Map<String, Object> parseProductForm() {
        String n = name != null && name.getText() != null ? name.getText().trim() : "";
        String p = price != null && price.getText() != null ? price.getText().trim() : "";
        String s = stock != null && stock.getText() != null ? stock.getText().trim() : "";
        String d = description != null && description.getText() != null ? description.getText().trim() : "";
        CategoryRow cat = category != null ? category.getSelectionModel().getSelectedItem() : null;

        if (n.isBlank()) {
            setStatus("Name is required.", true);
            return null;
        }
        if (cat == null || cat.categoryId.get() <= 0) {
            setStatus("Category is required.", true);
            return null;
        }

        double priceVal;
        int stockVal;
        try {
            priceVal = Double.parseDouble(p);
        } catch (Exception e) {
            setStatus("Invalid price.", true);
            return null;
        }
        try {
            stockVal = Integer.parseInt(s);
        } catch (Exception e) {
            setStatus("Invalid stock.", true);
            return null;
        }

        if (priceVal <= 0) {
            setStatus("Price must be > 0.", true);
            return null;
        }
        if (stockVal < 0) {
            setStatus("Stock must be >= 0.", true);
            return null;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("category_id", cat.categoryId.get());
        payload.put("name", n);
        payload.put("description", d);
        payload.put("price", priceVal);
        payload.put("stock", stockVal);
        return payload;
    }

    // ── Category actions (ADD/UPDATE/DELETE_CATEGORY) ────────────────────────
    @FXML
    private void handleAddCategory() {
        setStatus(null, false);
        String n = categoryNameField != null && categoryNameField.getText() != null ? categoryNameField.getText().trim() : "";
        String d = categoryDescriptionField != null && categoryDescriptionField.getText() != null ? categoryDescriptionField.getText().trim() : "";
        if (n.isBlank()) {
            setStatus("Category name is required.", true);
            return;
        }

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("name", n);
                payload.put("description", d);
                return client.send(new Request("ADD_CATEGORY", payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Add category failed.", true);
                return;
            }
            setStatus("Category created.", false);
            handleClearCategorySelection();
            loadCategories();
            loadProducts(); // refresh category labels
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while adding category.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleUpdateCategory() {
        setStatus(null, false);
        if (editingCategoryId == null) {
            setStatus("Select a category to update.", true);
            return;
        }
        String n = categoryNameField != null && categoryNameField.getText() != null ? categoryNameField.getText().trim() : "";
        String d = categoryDescriptionField != null && categoryDescriptionField.getText() != null ? categoryDescriptionField.getText().trim() : "";
        if (n.isBlank()) {
            setStatus("Category name is required.", true);
            return;
        }

        int id = editingCategoryId;
        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("id", id);
                payload.put("name", n);
                payload.put("description", d);
                return client.send(new Request("UPDATE_CATEGORY", payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Update category failed.", true);
                return;
            }
            setStatus("Category updated.", false);
            loadCategories();
            loadProducts();
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while updating category.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleDeleteCategory() {
        setStatus(null, false);
        CategoryRow row = categoriesTable != null ? categoriesTable.getSelectionModel().getSelectedItem() : null;
        if (row == null) {
            setStatus("Select a category to delete.", true);
            return;
        }
        int id = row.categoryId.get();

        Task<Response> t = new Task<>() {
            @Override protected Response call() throws Exception {
                Client client = Client.getInstance();
                client.connect();
                JSONObject payload = new JSONObject();
                payload.put("id", id);
                return client.send(new Request("DELETE_CATEGORY", payload, client.getSessionToken()));
            }
        };

        setBusy(true);
        t.setOnSucceeded(e -> {
            setBusy(false);
            Response r = t.getValue();
            if (r == null || !r.isSuccess()) {
                setStatus(r != null ? r.getMessage() : "Delete category failed.", true);
                return;
            }
            setStatus("Category deleted.", false);
            handleClearCategorySelection();
            loadCategories();
            loadProducts();
        });
        t.setOnFailed(e -> {
            setBusy(false);
            setStatus("Network error while deleting category.", true);
        });
        runTask(t);
    }

    @FXML
    private void handleClearCategorySelection() {
        editingCategoryId = null;
        if (categoriesTable != null) categoriesTable.getSelectionModel().clearSelection();
        if (categoryNameField != null) categoryNameField.clear();
        if (categoryDescriptionField != null) categoryDescriptionField.clear();
    }

    @FXML
    private void handleRefreshAll() {
        loadCategories();
        loadProducts();
    }

    // ── UI helpers ───────────────────────────────────────────────────────────
    private void setBusy(boolean busy) {
        Platform.runLater(() -> {
            if (addProductButton != null) addProductButton.setDisable(busy);
            if (updateProductButton != null) updateProductButton.setDisable(busy);
            if (deleteProductButton != null) deleteProductButton.setDisable(busy);
            if (clearProductButton != null) clearProductButton.setDisable(busy);

            if (addCategoryButton != null) addCategoryButton.setDisable(busy);
            if (updateCategoryButton != null) updateCategoryButton.setDisable(busy);
            if (deleteCategoryButton != null) deleteCategoryButton.setDisable(busy);
            if (clearCategoryButton != null) clearCategoryButton.setDisable(busy);
        });
    }

    private void updateProductToggleButton(ProductRow row) {
        Platform.runLater(() -> {
            if (deleteProductButton == null) return;
            if (row == null) {
                deleteProductButton.setText("Disable");
                return;
            }
            deleteProductButton.setText(row.available.get() ? "Disable" : "Enable");
        });
    }

    private void setStatus(String msg, boolean error) {
        if (statusLabel == null) return;
        Platform.runLater(() -> {
            if (msg == null || msg.isBlank()) {
                statusLabel.setText("");
                statusLabel.setVisible(false);
                statusLabel.setManaged(false);
                return;
            }
            statusLabel.setText(msg);
            statusLabel.getStyleClass().setAll(error ? "global-error" : "success-label");
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        });
    }

    private void runTask(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}

