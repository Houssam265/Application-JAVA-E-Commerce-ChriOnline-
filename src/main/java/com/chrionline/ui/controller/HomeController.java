package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.ClientSession;
import com.chrionline.ui.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Rectangle2D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
 * 3. Filtrage dynamique par : Nom (barre de recherche), Catégorie (ComboBox).
 * 4. Bouton "Voir détail" et indicateur d'indisponibilité si stock=0.
 */
public class HomeController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private FlowPane productsGrid;
    @FXML private Button adminButton;
    @FXML private Label connectedLabel;

    // TODO: A remplacer par un fetch via Socket/TCP au serveur lors du lien backend complet
    private List<Product> allProducts;
    private final Map<Integer, String> categoryNameById = new HashMap<>();
    private final Map<String, Integer> categoryIdByName = new HashMap<>();

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        if (session.isAdmin() && adminButton != null) {
            adminButton.setVisible(true);
            adminButton.setManaged(true);
        }
        if (connectedLabel != null && session.getUsername() != null && !session.getUsername().isBlank()) {
            connectedLabel.setText("Connecté · " + session.getUsername());
        }

        // Init collections (DB-driven)
        allProducts = new ArrayList<>();
        categoryCombo.getItems().setAll("Toutes");
        categoryCombo.setValue("Toutes");

        // Ajout des écouteurs pour la barre de recherche et filtres de catégorie en temps réel
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterProducts());
        categoryCombo.valueProperty().addListener((observable, oldValue, newValue) -> filterProducts());

        // Load categories + products from server (DB)
        loadCatalogueFromServer();
    }

    @FXML
    private void handleOpenCart() {
        SceneManager.showCart();
    }

    @FXML
    private void handleOpenAdmin() {
        SceneManager.showAdmin();
    }

    /**
     * Filtre les produits selon le texte de recherche et la catégorie sélectionnée.
     */
    private void filterProducts() {
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = categoryCombo.getValue();

        int targetCategoryId = -1; // -1 = Tous
        if (selectedCategory != null && !"Toutes".equals(selectedCategory)) {
            targetCategoryId = categoryIdByName.getOrDefault(selectedCategory, -1);
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
            List<String> names = new ArrayList<>();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                int id = c.optInt("categoryId", c.optInt("category_id", 0));
                String name = c.optString("name", "");
                if (id <= 0 || name.isBlank()) continue;
                byId.put(id, name);
                byName.put(name, id);
                names.add(name);
            }

            Platform.runLater(() -> {
                categoryNameById.clear();
                categoryIdByName.clear();
                categoryNameById.putAll(byId);
                categoryIdByName.putAll(byName);

                categoryCombo.getItems().setAll("Toutes");
                categoryCombo.getItems().addAll(names);
                if (categoryCombo.getValue() == null) categoryCombo.setValue("Toutes");
            });
        } catch (Exception ignored) {}
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
                pr.setImageUrl(p.optString("imageUrl", p.optString("image_url", "")));
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
        VBox card = new VBox(14);
        card.getStyleClass().addAll("product-card", "product-card--rich");
        card.setPrefWidth(280);
        card.setPrefHeight(250);

        // Header: catégorie + stock (pill)
        Label categoryLabel = new Label(getCategoryName(p.getCategoryId()));
        categoryLabel.getStyleClass().add("product-category");

        Label stockLabel = new Label();
        boolean isAvailable = p.getStock() > 0;
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
        media.setPrefHeight(120);

        ImageView img = new ImageView();
        img.getStyleClass().add("product-image");
        // Fixed box for consistent UI (cover crop)
        img.setPreserveRatio(true);
        img.setFitWidth(280 - 48); // card width minus padding (~24*2)
        img.setFitHeight(120);
        img.setSmooth(true);
        img.setCache(true);

        Label placeholder = new Label("Aucune image");
        placeholder.getStyleClass().add("image-placeholder");

        String url = p.getImageUrl();
        if (url != null) url = url.trim();
        if (url != null && !url.isBlank() && !"null".equalsIgnoreCase(url)) {
            try {
                // background loading
                Image image = new Image(url, true);
                img.setImage(image);
                // Apply center-crop once image is loaded
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

        // Title + short description
        Label nameLabel = new Label(p.getName());
        nameLabel.getStyleClass().add("product-title");
        nameLabel.setWrapText(true);

        Label descLabel = new Label(p.getDescription() == null ? "" : p.getDescription());
        descLabel.getStyleClass().addAll("product-desc", "body-text");
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(44); // ~2 lignes

        // Footer: prix + CTA
        Label priceLabel = new Label(String.format("%.2f Dhs", p.getPrice()));
        priceLabel.getStyleClass().add("product-price");

        Button detailButton = new Button("Voir détail →");
        detailButton.getStyleClass().add("btn-outline");
        detailButton.setOnAction(event -> com.chrionline.ui.SceneManager.showProductDetail(p));

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        HBox footer = new HBox(12, priceLabel, footerSpacer, detailButton);
        footer.getStyleClass().add("product-card__footer");
        footer.setFillHeight(true);

        card.getChildren().addAll(header, media, nameLabel, descLabel, footer);
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
}
