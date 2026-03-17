package com.chrionline.service;

import com.chrionline.dao.CartDAO;
import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.ProductDAO;
import com.chrionline.model.Cart;
import com.chrionline.model.CartItem;
import com.chrionline.model.Order;
import com.chrionline.model.OrderItem;
import com.chrionline.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for order placement from a cart.
 */
public class OrderService {

    private final CartDAO cartDAO = new CartDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();

    /**
     * Converts the user's cart into an Order and clears the cart.
     * Note: order header+items are transactional in OrderDAO; stock/cart clearing are best-effort.
     */
    public Order placeOrderFromCart(int userId) {
        Cart cart = cartDAO.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("Panier introuvable."));
        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Votre panier est vide.");
        }

        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(cart.calculateTotal());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        for (CartItem ci : items) {
            // Basic stock check (same logic as cart add)
            if (!productDAO.isAvailable(ci.getProductId(), ci.getQuantity())) {
                throw new IllegalArgumentException("Stock insuffisant pour un ou plusieurs articles.");
            }
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getOrderId());
            oi.setProductId(ci.getProductId());
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(ci.getUnitPrice());
            order.getItems().add(oi);
        }

        orderDAO.save(order);

        // Post-actions (not wrapped in OrderDAO transaction)
        cartDAO.clearCart(cart.getCartId());
        return order;
    }

    public java.util.List<Order> getOrdersForUser(int userId) {
        return orderDAO.findByUserId(userId);
    }

    public java.util.List<Order> getAllOrders() {
        return orderDAO.findAll();
    }

    public void updateOrderStatus(String orderId, OrderStatus status) {
        orderDAO.updateStatus(orderId, status);
    }
}

