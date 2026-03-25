package com.chrionline.ui.controller;

import com.chrionline.client.Client;
import com.chrionline.model.Product;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.ui.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    @FXML private ImageView productImageView;
    @FXML private Label imagePlaceholderLabel;

    private Product product;

    @FXML
    public void initialize() {
        // Make the spinner editor show the current value clearly.
        // (A TCP client/UI theme can hide the editor text if the spinner isn't editable.)
        if (quantitySpinner != null) {
            quantitySpinner.getStyleClass().add("premium-spinner");
            quantitySpinner.setEditable(true);
        }
    }

    public void setProduct(Product product) {
        this.product = product;
        productNameLabel.setText(product.getName());
        productPriceLabel.setText(String.format("%.2f Dhs", product.getPrice()));
        
        // Mock description if empty
        String desc = product.getDescription();
        productDescriptionLabel.setText((desc != null && !desc.isEmpty()) ? desc : "Aucune description disponible pour ce produit.");
        
        productCategoryLabel.setText(getCategoryName(product.getCategoryId()));

        // Image (URL stored in products.image_url)
        if (productImageView != null && imagePlaceholderLabel != null) {
            String url = product.getImageUrl();
            if (url != null) url = url.trim();
            boolean hasUrl = url != null && !url.isBlank() && !"null".equalsIgnoreCase(url);
            if (hasUrl) {
                try {
                    String normalized = normalizeImageUrlForLocalFiles(url);
                    Image image = new Image(normalized, true); // background loading
                    productImageView.setImage(image);
                    image.progressProperty().addListener((obs, ov, nv) -> {
                        if (nv != null && nv.doubleValue() >= 1.0) {
                            javafx.application.Platform.runLater(() ->
                                    applyCoverViewport(productImageView, image, productImageView.getFitWidth(), productImageView.getFitHeight()));
                        }
                    });
                    imagePlaceholderLabel.setVisible(false);
                    imagePlaceholderLabel.setManaged(false);
                } catch (Exception ignored) {
                    productImageView.setImage(null);
                    imagePlaceholderLabel.setVisible(true);
                    imagePlaceholderLabel.setManaged(true);
                }
            } else {
                productImageView.setImage(null);
                imagePlaceholderLabel.setVisible(true);
                imagePlaceholderLabel.setManaged(true);
            }
        }

        if (product.isAvailable() && product.getStock() > 0) {
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
        if (product != null && product.isAvailable() && product.getStock() > 0) {
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

    private static String normalizeImageUrlForLocalFiles(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return null;
        String lower = u.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return u;
        return new java.io.File(u).toURI().toString();
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
