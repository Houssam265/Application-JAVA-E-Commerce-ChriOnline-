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
     * This operation is end-to-end transactional in {@link OrderDAO#placeOrderFromCart(int)}:
     * stock check, order creation, stock decrement, cart clearing.
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

