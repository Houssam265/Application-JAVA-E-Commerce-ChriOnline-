package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONObject;

public class ProductDetailController {

    @FXML private Label productNameLabel;
    @FXML private Label productCategoryLabel;
    @FXML private Label productPriceLabel;
    @FXML private Label productDescriptionLabel;
    @FXML private Label stockStatusLabel;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button addToCartButton;
    @FXML private Label messageLabel;

    private Product product;

    @FXML
    public void initialize() {
        // Initialization if needed
    }

    public void setProduct(Product product) {
        this.product = product;
        productNameLabel.setText(product.getName());
        productPriceLabel.setText(String.format("%.2f €", product.getPrice()));
        
        // Mock description if empty
        String desc = product.getDescription();
        productDescriptionLabel.setText((desc != null && !desc.isEmpty()) ? desc : "Aucune description disponible pour ce produit.");
        
        productCategoryLabel.setText(getCategoryName(product.getCategoryId()));

        if (product.getStock() > 0) {
            stockStatusLabel.setText("En stock (" + product.getStock() + ")");
            stockStatusLabel.getStyleClass().setAll("badge-instock");
            
            SpinnerValueFactory<Integer> valueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, product.getStock(), 1);
            quantitySpinner.setValueFactory(valueFactory);
            
            quantitySpinner.setDisable(false);
            addToCartButton.setDisable(false);
            messageLabel.setVisible(false);
        } else {
            stockStatusLabel.setText("Indisponible");
            stockStatusLabel.getStyleClass().setAll("badge-outstock");
            
            SpinnerValueFactory<Integer> valueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 0, 0);
            quantitySpinner.setValueFactory(valueFactory);
            
            quantitySpinner.setDisable(true);
            addToCartButton.setDisable(true);
            
            messageLabel.setText("Ce produit est actuellement en rupture de stock.");
            messageLabel.getStyleClass().setAll("error-label");
            messageLabel.setVisible(true);
        }
    }

    @FXML
    private void handleAddToCart() {
        if (product != null && product.getStock() > 0) {
            int quantity = quantitySpinner.getValue();
            Task<Response> t = new Task<>() {
                @Override protected Response call() throws Exception {
                    Client client = Client.getInstance();
                    client.connect();
                    JSONObject payload = new JSONObject();
                    payload.put("product_id", product.getProductId());
                    payload.put("quantity", quantity);
                    payload.put("unit_price", product.getPrice());
                    return client.send(new Request(MessageProtocol.ACTION_ADD_TO_CART, payload, client.getSessionToken()));
                }
            };
            t.setOnSucceeded(e -> {
                Response r = t.getValue();
                if (!r.isSuccess()) {
                    messageLabel.setText(r.getMessage().isBlank() ? "Impossible d'ajouter au panier." : r.getMessage());
                    messageLabel.getStyleClass().setAll("global-error");
                    messageLabel.setVisible(true);
                    return;
                }
                messageLabel.setText("Ajouté au panier (" + quantity + ") !");
                messageLabel.getStyleClass().setAll("success-label");
                messageLabel.setVisible(true);
            });
            t.setOnFailed(e -> {
                messageLabel.setText("Erreur réseau lors de l'ajout au panier.");
                messageLabel.getStyleClass().setAll("global-error");
                messageLabel.setVisible(true);
            });
            Thread th = new Thread(t);
            th.setDaemon(true);
            th.start();
        }
    }

    @FXML
    private void handleBackToCatalog() {
        SceneManager.showHome();
    }

    private String getCategoryName(int categoryId) {
        switch (categoryId) {
            case 1: return "PC Portables";
            case 2: return "Smartphones";
            case 3: return "Accessoires";
            default: return "Autre";
        }
    }
}
