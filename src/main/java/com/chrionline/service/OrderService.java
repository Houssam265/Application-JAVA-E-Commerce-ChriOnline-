package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.PaymentDAO;
import com.chrionline.model.Order;
import com.chrionline.model.OrderStatus;
import com.chrionline.model.Payment;

public class OrderService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    public Order placeOrderFromCart(int userId) {
        return orderDAO.placeOrderFromCart(userId);
    }

    public java.util.List<Order> getOrdersForUser(int userId) {
        return orderDAO.findByUserId(userId);
    }

    public java.util.List<Order> getAllOrders() {
        return orderDAO.findAll();
    }

    public void updateOrderStatus(int orderId, OrderStatus status) {
        orderDAO.updateStatus(orderId, status);
    }

    public java.util.Optional<Order> getOrderDetails(int orderId) {
        return orderDAO.findById(orderId);
    }

    public java.util.Optional<java.util.Map<String, Object>> getOrderDetailsWithTimeline(int orderId) {
        java.util.Optional<Order> opt = orderDAO.findById(orderId);
        if (opt.isEmpty()) return java.util.Optional.empty();
        Order o = opt.get();

        java.util.List<java.util.Map<String, String>> timeline = new java.util.ArrayList<>();
        java.time.LocalDateTime createdAt = o.getCreatedAt();
        java.time.LocalDateTime last = null;
        if (createdAt != null) {
            java.util.Map<String, String> step = new java.util.HashMap<>();
            step.put("status", "CREATED");
            step.put("at", createdAt.toString());
            timeline.add(step);
            last = createdAt;
        }

        java.util.Optional<Payment> pay = paymentDAO.findByOrderId(orderId);
        java.time.LocalDateTime paidAt = null;
        if (pay.isPresent() && pay.get().getStatus() == Payment.Status.SUCCESS) {
            paidAt = pay.get().getPaidAt();
        }

        if (o.getStatus() == OrderStatus.VALIDATED || o.getStatus() == OrderStatus.SHIPPED || o.getStatus() == OrderStatus.DELIVERED) {
            java.time.LocalDateTime validatedAt = paidAt;
            java.time.LocalDateTime updatedAt = o.getUpdatedAt();
            if (validatedAt == null || (updatedAt != null && updatedAt.isAfter(validatedAt) && o.getStatus() == OrderStatus.VALIDATED)) {
                validatedAt = updatedAt;
            }
            if (validatedAt == null) validatedAt = last;
            if (validatedAt != null) {
                if (last != null && validatedAt.isBefore(last)) validatedAt = last;
                java.util.Map<String, String> step = new java.util.HashMap<>();
                step.put("status", "VALIDATED");
                step.put("at", validatedAt.toString());
                timeline.add(step);
                last = validatedAt;
            }
        }

        if (o.getStatus() == OrderStatus.SHIPPED || o.getStatus() == OrderStatus.DELIVERED) {
            java.util.Map<String, String> step = new java.util.HashMap<>();
            step.put("status", "SHIPPED");
            java.time.LocalDateTime shippedAt = o.getUpdatedAt();
            if (shippedAt != null && last != null && shippedAt.isBefore(last)) shippedAt = last;
            step.put("at", shippedAt != null ? shippedAt.toString() : (last != null ? last.toString() : ""));
            timeline.add(step);
            if (shippedAt != null) last = shippedAt;
        }

        if (o.getStatus() == OrderStatus.DELIVERED) {
            java.util.Map<String, String> step = new java.util.HashMap<>();
            step.put("status", "DELIVERED");
            java.time.LocalDateTime deliveredAt = o.getUpdatedAt();
            if (deliveredAt != null && last != null && deliveredAt.isBefore(last)) deliveredAt = last;
            step.put("at", deliveredAt != null ? deliveredAt.toString() : (last != null ? last.toString() : ""));
            timeline.add(step);
        }

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("order", o);
        payload.put("timeline", timeline);
        return java.util.Optional.of(payload);
    }
}
