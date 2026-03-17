package com.chrionline.service;

import com.chrionline.dao.CartDAO;
import com.chrionline.dao.ProductDAO;
import com.chrionline.model.Cart;
import com.chrionline.model.CartItem;
import com.chrionline.model.Product;

import java.util.*;

/**
 * Business logic for cart operations (server-side).
 */
public class CartService {

    private final CartDAO cartDAO = new CartDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public Cart getOrCreateCart(int userId) {
        return cartDAO.findByUserId(userId).orElseGet(() -> cartDAO.createForUser(userId));
    }

    public void addToCart(int userId, int productId, int quantity, double unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("La quantité doit être > 0");
        Cart cart = getOrCreateCart(userId);
        // Ensure we have the latest items to compute existing quantity
        cart = cartDAO.findByUserId(userId).orElse(cart);

        Product product = productDAO.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable (id=" + productId + ")."));

        int alreadyInCart = cart.getItems().stream()
                .filter(it -> it.getProductId() == productId)
                .mapToInt(CartItem::getQuantity)
                .findFirst()
                .orElse(0);

        int requestedTotal = alreadyInCart + quantity;
        if (product.getStock() < requestedTotal) {
            throw new IllegalArgumentException(
                    "Stock insuffisant pour ce produit. Stock=" + product.getStock() + ", demandé=" + requestedTotal + ".");
        }

        cartDAO.addItem(cart.getCartId(), productId, quantity, unitPrice);
    }

    public void updateCartItemQuantity(int cartItemId, int newQuantity) {
        if (newQuantity <= 0) throw new IllegalArgumentException("La quantité doit être > 0");
        cartDAO.updateItemQuantity(cartItemId, newQuantity);
    }

    public void removeCartItem(int cartItemId) {
        cartDAO.removeItem(cartItemId);
    }

    public void clearCartForUser(int userId) {
        Cart cart = getOrCreateCart(userId);
        cartDAO.clearCart(cart.getCartId());
    }

    /**
     * Returns a cart payload enriched with product names for UI rendering.
     */
    public Map<String, Object> getCartView(int userId) {
        Cart cart = getOrCreateCart(userId);
        // Reload items
        cart = cartDAO.findByUserId(userId).orElse(cart);

        List<Map<String, Object>> items = new ArrayList<>();
        for (CartItem it : cart.getItems()) {
            Optional<Product> p = productDAO.findById(it.getProductId());
            Map<String, Object> row = new HashMap<>();
            row.put("cartItemId", it.getCartItemId());
            row.put("productId", it.getProductId());
            row.put("name", p.map(Product::getName).orElse("Produit"));
            row.put("unitPrice", it.getUnitPrice());
            row.put("quantity", it.getQuantity());
            row.put("subtotal", it.getSubtotal());
            items.add(row);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("cartId", cart.getCartId());
        payload.put("userId", cart.getUserId());
        payload.put("items", items);
        payload.put("total", cart.calculateTotal());
        return payload;
    }
}

