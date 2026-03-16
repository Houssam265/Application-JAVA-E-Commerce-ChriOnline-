package com.chrionline.ui.controller;

import com.chrionline.model.Product;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;
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

    // TODO: A remplacer par un fetch via Socket/TCP au serveur lors du lien backend complet
    private List<Product> allProducts;

    @FXML
    public void initialize() {
        // Initialisation de la ComboBox des catégories
        categoryCombo.getItems().addAll("Toutes", "PC Portables", "Smartphones", "Accessoires");
        categoryCombo.setValue("Toutes");

        // Alimentation des produits simulés (Mocks) pour tester l'UI KAN-6
        allProducts = new ArrayList<>();
        allProducts.add(new Product(1, 1, "MacBook Pro M3", "Puce M3 Max, 32Go RAM, 1To SSD.", 2499.00, 15, ""));
        allProducts.add(new Product(2, 2, "iPhone 15 Pro", "Titane naturel, 256Go.", 1199.00, 0, ""));
        allProducts.add(new Product(3, 1, "Dell XPS 13 Plus", "Écran OLED, 16Go RAM, 512Go SSD.", 1499.00, 5, ""));
        allProducts.add(new Product(4, 3, "AirPods Pro 2", "Réduction de bruit active, USB-C.", 279.00, 20, ""));
        allProducts.add(new Product(5, 2, "Samsung Galaxy S24 Ultra", "Caméra 200MP, S-Pen inclu.", 1299.00, 10, ""));
        allProducts.add(new Product(6, 3, "Souris Logitech MX Master 3S", "Ergonomique et silencieuse.", 99.00, 0, ""));
        allProducts.add(new Product(7, 1, "Asus ROG Zephyrus G14", "PC Gamer portable, RTX 4060.", 1799.00, 3, ""));

        // Affichage initial de tous les produits
        displayProducts(allProducts);

        // Ajout des écouteurs pour la barre de recherche et filtres de catégorie en temps réel
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterProducts());
        categoryCombo.valueProperty().addListener((observable, oldValue, newValue) -> filterProducts());
    }

    /**
     * Filtre les produits selon le texte de recherche et la catégorie sélectionnée.
     */
    private void filterProducts() {
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = categoryCombo.getValue();

        int targetCategoryId = -1; // -1 = Tous
        if ("PC Portables".equals(selectedCategory)) targetCategoryId = 1;
        else if ("Smartphones".equals(selectedCategory)) targetCategoryId = 2;
        else if ("Accessoires".equals(selectedCategory)) targetCategoryId = 3;

        int finalTargetId = targetCategoryId;
        List<Product> filteredList = allProducts.stream()
                .filter(p -> p.getName().toLowerCase().contains(searchText))
                .filter(p -> finalTargetId == -1 || p.getCategoryId() == finalTargetId)
                .collect(Collectors.toList());

        displayProducts(filteredList);
    }

    /**
     * Rafraichit la grille des produits.
     */
    private void displayProducts(List<Product> products) {
        productsGrid.getChildren().clear();

        if (products.isEmpty()) {
            Label noResults = new Label("Aucun produit ne correspond à votre recherche.");
            noResults.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
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
        card.getStyleClass().add("product-card");
        card.setPrefWidth(260); // Largeur de chaque carte produit
        card.setPrefHeight(220); // Hauteur pour l'uniformité

        // Catégorie
        Label categoryLabel = new Label(getCategoryName(p.getCategoryId()));
        categoryLabel.getStyleClass().add("product-category");

        // Titre produit
        Label nameLabel = new Label(p.getName());
        nameLabel.getStyleClass().add("product-title");
        nameLabel.setWrapText(true);

        // Prix
        Label priceLabel = new Label(p.getPrice() + " €");
        priceLabel.getStyleClass().add("product-price");

        // Disponibilité
        Label stockLabel = new Label();
        boolean isAvailable = p.getStock() > 0;
        
        if (isAvailable) {
            stockLabel.setText("En stock (" + p.getStock() + ")");
            stockLabel.getStyleClass().add("badge-instock");
        } else {
            stockLabel.setText("Indisponible");
            stockLabel.getStyleClass().add("badge-outstock");
        }

        // Pousseur pour forcer le bouton en bas de la carte
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Bouton détail
        Button detailButton = new Button("Voir détail");
        detailButton.getStyleClass().add("btn-secondary");
        detailButton.setMaxWidth(Double.MAX_VALUE);
        
        detailButton.setOnAction(event -> {
            // TODO: Rediriger vers l'écran de détail du Produit (ProductController)
            System.out.println("Clic sur Produit : " + p.getName());
        });

        card.getChildren().addAll(categoryLabel, nameLabel, priceLabel, stockLabel, spacer, detailButton);
        return card;
    }

    /**
     * Convertit l'ID Catégorie en Texte (mock simple).
     */
    private String getCategoryName(int categoryId) {
        return switch (categoryId) {
            case 1 -> "PC Portables";
            case 2 -> "Smartphones";
            case 3 -> "Accessoires";
            default -> "Autre";
        };
    }
}
