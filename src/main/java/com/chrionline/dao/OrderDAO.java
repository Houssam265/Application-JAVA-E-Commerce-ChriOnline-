package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Order;
import com.chrionline.model.OrderItem;
import com.chrionline.model.OrderStatus;
import com.chrionline.model.Payment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public Order placeOrderFromCart(int userId) {
        final String selectCartId = "SELECT cart_id FROM carts WHERE user_id = ? LIMIT 1";
        final String selectCartItems =
                "SELECT cart_item_id, cart_id, product_id, quantity, unit_price " +
                "FROM cart_items WHERE cart_id = ?";
        final String lockProduct = "SELECT stock FROM products WHERE product_id = ? FOR UPDATE";
        final String insertOrder =
                "INSERT INTO orders (user_id, status, total_amount) VALUES (?, ?, ?)";
        final String insertItem =
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";
        final String clearCart = "DELETE FROM cart_items WHERE cart_id = ?";

        Connection c = conn();
        try {
            c.setAutoCommit(false);

            Integer cartId = null;
            try (PreparedStatement ps = c.prepareStatement(selectCartId)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) cartId = rs.getInt("cart_id");
                }
            }
            if (cartId == null) throw new IllegalArgumentException("Panier introuvable.");

            List<OrderItem> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(selectCartItems)) {
                ps.setInt(1, cartId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OrderItem oi = new OrderItem();
                        oi.setProductId(rs.getInt("product_id"));
                        oi.setQuantity(rs.getInt("quantity"));
                        oi.setUnitPrice(rs.getDouble("unit_price"));
                        items.add(oi);
                    }
                }
            }
            if (items.isEmpty()) throw new IllegalArgumentException("Votre panier est vide.");

            try (PreparedStatement psLock = c.prepareStatement(lockProduct)) {
                for (OrderItem it : items) {
                    if (it.getQuantity() <= 0) throw new IllegalArgumentException("Quantité invalide dans le panier.");
                    psLock.setInt(1, it.getProductId());
                    try (ResultSet rs = psLock.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Produit introuvable (id=" + it.getProductId() + ").");
                        }
                        int stock = rs.getInt("stock");
                        if (stock < it.getQuantity()) {
                            throw new IllegalArgumentException("Stock insuffisant pour le produit id=" + it.getProductId() + ".");
                        }
                    }
                }
            }

            double total = 0.0;
            for (OrderItem it : items) {
                if (it.getUnitPrice() <= 0) throw new IllegalArgumentException("Prix invalide dans le panier.");
                total += it.getUnitPrice() * it.getQuantity();
            }

            int orderId;
            try (PreparedStatement psOrder = c.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                psOrder.setInt(1, userId);
                psOrder.setString(2, OrderStatus.PENDING.name());
                psOrder.setDouble(3, total);
                psOrder.executeUpdate();
                try (ResultSet keys = psOrder.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Aucun order_id généré.");
                    orderId = keys.getInt(1);
                }
            }

            try (PreparedStatement psItem = c.prepareStatement(insertItem)) {
                for (OrderItem it : items) {
                    psItem.setInt(1, orderId);
                    psItem.setInt(2, it.getProductId());
                    psItem.setInt(3, it.getQuantity());
                    psItem.setDouble(4, it.getUnitPrice());
                    psItem.addBatch();
                }
                psItem.executeBatch();
            }

            try (PreparedStatement ps = c.prepareStatement(clearCart)) {
                ps.setInt(1, cartId);
                ps.executeUpdate();
            }

            c.commit();

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setStatus(OrderStatus.PENDING);
            order.setTotalAmount(total);
            order.setItems(items);
            return order;
        } catch (IllegalArgumentException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw e;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("OrderDAO.placeOrderFromCart failed for userId=" + userId + ": " + e.getMessage(), e);
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public Order save(Order order) {
        final String insertOrder = "INSERT INTO orders (user_id, status, total_amount) VALUES (?, ?, ?)";
        final String insertItem = "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";

        Connection c = conn();
        try {
            c.setAutoCommit(false);

            int orderId;
            try (PreparedStatement psOrder = c.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                psOrder.setInt(1, order.getUserId());
                psOrder.setString(2, order.getStatus().name());
                psOrder.setDouble(3, order.getTotalAmount());
                psOrder.executeUpdate();
                try (ResultSet keys = psOrder.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Aucun order_id généré.");
                    orderId = keys.getInt(1);
                }
            }

            try (PreparedStatement psItem = c.prepareStatement(insertItem)) {
                for (OrderItem item : order.getItems()) {
                    psItem.setInt(1, orderId);
                    psItem.setInt(2, item.getProductId());
                    psItem.setInt(3, item.getQuantity());
                    psItem.setDouble(4, item.getUnitPrice());
                    psItem.addBatch();
                }
                psItem.executeBatch();
            }

            c.commit();
            order.setOrderId(orderId);
            return order;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("OrderDAO.save failed: " + e.getMessage(), e);
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public Optional<Order> findById(int orderId) {
        final String sql = "SELECT * FROM orders WHERE order_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Order order = mapOrderRow(rs);
                order.setItems(getItems(orderId));
                return Optional.of(order);
            }
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findById failed for orderId=" + orderId + ": " + e.getMessage(), e);
        }
    }

    public List<Order> findByUserId(int userId) {
        final String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC, order_id DESC";
        List<Order> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = mapOrderRow(rs);
                    order.setItems(getItems(order.getOrderId()));
                    result.add(order);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findByUserId failed: " + e.getMessage(), e);
        }
    }

    public List<Order> findAll() {
        final String sql = "SELECT * FROM orders ORDER BY created_at DESC, order_id DESC";
        List<Order> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Order order = mapOrderRow(rs);
                order.setItems(getItems(order.getOrderId()));
                result.add(order);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    public void updateStatus(int orderId, OrderStatus status) {
        final String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.updateStatus failed for orderId=" + orderId + ": " + e.getMessage(), e);
        }
    }

    public void completePaymentWithStockDecrement(int orderId,
                                                  Payment.Method method,
                                                  double amount,
                                                  String transactionId,
                                                  LocalDateTime paidAt) {
        Connection c = conn();
        final String lockOrder = "SELECT status FROM orders WHERE order_id = ? FOR UPDATE";
        final String lockProduct = "SELECT stock FROM products WHERE product_id = ? FOR UPDATE";
        final String decStock = "UPDATE products SET stock = stock - ? WHERE product_id = ?";
        final String updateOrder = "UPDATE orders SET status = ? WHERE order_id = ?";

        try {
            c.setAutoCommit(false);

            String currentStatus;
            try (PreparedStatement ps = c.prepareStatement(lockOrder)) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Commande introuvable.");
                    currentStatus = rs.getString("status");
                }
            }
            if (OrderStatus.VALIDATED.name().equalsIgnoreCase(currentStatus)
                    || OrderStatus.SHIPPED.name().equalsIgnoreCase(currentStatus)
                    || OrderStatus.DELIVERED.name().equalsIgnoreCase(currentStatus)) {
                throw new IllegalArgumentException("Cette commande est déjà payée.");
            }

            List<OrderItem> items = getItems(c, orderId);
            if (items.isEmpty()) throw new IllegalArgumentException("Commande vide.");

            try (PreparedStatement psLock = c.prepareStatement(lockProduct);
                 PreparedStatement psDec = c.prepareStatement(decStock)) {
                for (OrderItem item : items) {
                    psLock.setInt(1, item.getProductId());
                    try (ResultSet rs = psLock.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Produit introuvable (id=" + item.getProductId() + ").");
                        }
                        int stock = rs.getInt("stock");
                        if (stock < item.getQuantity()) {
                            throw new IllegalArgumentException("Stock insuffisant pour le produit id=" + item.getProductId() + ".");
                        }
                    }

                    psDec.setInt(1, item.getQuantity());
                    psDec.setInt(2, item.getProductId());
                    psDec.addBatch();
                }
                psDec.executeBatch();
            }

            PaymentDAO paymentDAO = new PaymentDAO();
            paymentDAO.upsertPayment(c, orderId, method, Payment.Status.SUCCESS, amount, transactionId, paidAt);

            try (PreparedStatement ps = c.prepareStatement(updateOrder)) {
                ps.setString(1, OrderStatus.VALIDATED.name());
                ps.setInt(2, orderId);
                ps.executeUpdate();
            }

            c.commit();
        } catch (IllegalArgumentException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw e;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("OrderDAO.completePaymentWithStockDecrement failed for orderId=" + orderId + ": " + e.getMessage(), e);
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public List<OrderItem> getItems(int orderId) {
        try {
            return getItems(conn(), orderId);
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.getItems failed for orderId=" + orderId + ": " + e.getMessage(), e);
        }
    }

    private List<OrderItem> getItems(Connection c, int orderId) throws SQLException {
        final String sql =
                "SELECT oi.order_item_id, oi.order_id, oi.product_id, " +
                "oi.quantity, oi.unit_price, p.name AS product_name " +
                "FROM order_items oi JOIN products p ON p.product_id = oi.product_id " +
                "WHERE oi.order_id = ?";

        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setOrderItemId(rs.getInt("order_item_id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private Order mapOrderRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setUserId(rs.getInt("user_id"));
        o.setStatus(OrderStatus.valueOf(rs.getString("status")));
        o.setTotalAmount(rs.getDouble("total_amount"));
        try {
            o.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        } catch (Exception ignored) {
            Timestamp ts = rs.getTimestamp("created_at");
            o.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        }
        try {
            o.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        } catch (Exception ignored) {
            Timestamp ts = rs.getTimestamp("updated_at");
            o.setUpdatedAt(ts != null ? ts.toLocalDateTime() : null);
        }
        return o;
    }
}
