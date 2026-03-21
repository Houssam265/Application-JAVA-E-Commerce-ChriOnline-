package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.model.Order;
import com.chrionline.model.OrderStatus;

/**
 * Business logic for order placement from a cart.
 */
public class OrderService {

    private final OrderDAO orderDAO = new OrderDAO();

    /**
     * Places an order from the user's cart.
     * Transaction dans {@link OrderDAO#placeOrderFromCart(int)} : vérification stock,
     * création commande PENDING, vidage panier. Le stock est décrémenté au paiement (KAN-19).
     */
    public Order placeOrderFromCart(int userId) {
        return orderDAO.placeOrderFromCart(userId);
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

