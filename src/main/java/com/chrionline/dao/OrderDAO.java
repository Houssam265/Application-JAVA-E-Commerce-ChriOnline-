package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Order;
import com.chrionline.model.OrderItem;
import com.chrionline.model.OrderStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code orders} and {@code order_items} tables.
 *
 * All queries use PreparedStatement — no string concatenation.
 * All JDBC resources are closed via try-with-resources.
 *
 * {@link #save(Order)} runs in a single transaction: if any order_item INSERT
 * fails, the whole operation is rolled back so the DB is never left in a
 * partial state.
 */
public class OrderDAO {

    // ── Connection helper ───────────────────────────────────────────────────
    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── PLACE ORDER FROM CART (transactional end-to-end) ─────────────────────

    /**
     * Converts the user's cart into an order in ONE database transaction:
     * - Re-checks stock in real-time (row locks)
     * - Inserts order header + order_items
     * - Decrements product stock
     * - Clears the cart
     *
     * <p>This is the "advanced-level" expected behavior: stock updates and order creation
     * are atomic (COMMIT) or not applied at all (ROLLBACK) on failure.</p>
     *
     * @param userId authenticated user placing the order
     * @return created order with items populated
     * @throws IllegalArgumentException if cart is missing/empty or stock insufficient
     * @throws RuntimeException on database errors (transaction rolled back)
     */
    public Order placeOrderFromCart(int userId) {
        final String selectCartId = "SELECT cart_id FROM carts WHERE user_id = ? LIMIT 1";
        final String selectCartItems =
                "SELECT cart_item_id, cart_id, product_id, quantity, unit_price " +
                "FROM cart_items WHERE cart_id = ?";
        final String lockProduct =
                "SELECT stock FROM products WHERE product_id = ? FOR UPDATE";
        final String insertOrder =
                "INSERT INTO orders (order_id, user_id, status, total_amount) VALUES (?, ?, ?, ?)";
        final String insertItem =
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";
        final String decrementStock =
                "UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?";
        final String clearCart =
                "DELETE FROM cart_items WHERE cart_id = ?";

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
            if (cartId == null) {
                throw new IllegalArgumentException("Panier introuvable.");
            }

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

            if (items.isEmpty()) {
                throw new IllegalArgumentException("Votre panier est vide.");
            }

            // Lock + validate stock for every product (prevents race conditions)
            try (PreparedStatement psLock = c.prepareStatement(lockProduct)) {
                for (OrderItem it : items) {
                    if (it.getQuantity() <= 0) {
                        throw new IllegalArgumentException("Quantité invalide dans le panier.");
                    }
                    psLock.setInt(1, it.getProductId());
                    try (ResultSet rs = psLock.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Produit introuvable (id=" + it.getProductId() + ").");
                        }
                        int stock = rs.getInt("stock");
                        if (stock < it.getQuantity()) {
                            throw new IllegalArgumentException(
                                    "Stock insuffisant pour le produit id=" + it.getProductId()
                                            + ". Stock=" + stock + ", demandé=" + it.getQuantity());
                        }
                    }
                }
            }

            // Compute total from cart snapshot prices (server wrote them when adding to cart)
            double total = 0.0;
            for (OrderItem it : items) {
                if (it.getUnitPrice() < 0) {
                    throw new IllegalArgumentException("Prix invalide dans le panier.");
                }
                total += it.getUnitPrice() * it.getQuantity();
            }

            String orderId = UUID.randomUUID().toString();

            // Insert order header
            try (PreparedStatement psOrder = c.prepareStatement(insertOrder)) {
                psOrder.setString(1, orderId);
                psOrder.setInt(2, userId);
                psOrder.setString(3, OrderStatus.PENDING.name());
                psOrder.setDouble(4, total);
                psOrder.executeUpdate();
            }

            // Insert order items
            try (PreparedStatement psItem = c.prepareStatement(insertItem)) {
                for (OrderItem it : items) {
                    psItem.setString(1, orderId);
                    psItem.setInt(2, it.getProductId());
                    psItem.setInt(3, it.getQuantity());
                    psItem.setDouble(4, it.getUnitPrice());
                    psItem.addBatch();
                }
                psItem.executeBatch();
            }

            // Decrement stock (conditional update) for every product
            try (PreparedStatement psUpd = c.prepareStatement(decrementStock)) {
                for (OrderItem it : items) {
                    psUpd.setInt(1, it.getQuantity());
                    psUpd.setInt(2, it.getProductId());
                    psUpd.setInt(3, it.getQuantity());
                    int updated = psUpd.executeUpdate();
                    if (updated != 1) {
                        throw new IllegalArgumentException("Stock insuffisant pour un ou plusieurs articles.");
                    }
                }
            }

            // Clear cart
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

    // ── INSERT orders + order_items (transactional) ──────────────────────────

    /**
     * Persists a new order and all its items in a single transaction.
     *
     * <p>The {@code order_id} must be a UUID string generated by the caller
     * via {@code UUID.randomUUID().toString()} — it is NOT auto-incremented
     * (the schema uses {@code CHAR(36)}).</p>
     *
     * @param order fully populated Order with a non-empty items list
     * @return the same order object (unchanged — order_id was set by caller)
     * @throws RuntimeException if the INSERT fails; the transaction is rolled back
     */
    public Order save(Order order) {
        final String insertOrder =
            "INSERT INTO orders (order_id, user_id, status, total_amount) VALUES (?, ?, ?, ?)";
        final String insertItem =
            "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";

        Connection c = conn();
        try {
            c.setAutoCommit(false);  // ── begin transaction ──

            // 1. Insert the order header
            try (PreparedStatement psOrder = c.prepareStatement(insertOrder)) {
                psOrder.setString(1, order.getOrderId());
                psOrder.setInt(2, order.getUserId());
                psOrder.setString(3, order.getStatus().name());
                psOrder.setDouble(4, order.getTotalAmount());
                psOrder.executeUpdate();
            }

            // 2. Insert every order_item in the same transaction
            try (PreparedStatement psItem = c.prepareStatement(insertItem)) {
                for (OrderItem item : order.getItems()) {
                    psItem.setString(1, order.getOrderId());
                    psItem.setInt(2, item.getProductId());
                    psItem.setInt(3, item.getQuantity());
                    psItem.setDouble(4, item.getUnitPrice());
                    psItem.addBatch();
                }
                psItem.executeBatch();
            }

            c.commit();               // ── commit ──
            return order;

        } catch (SQLException e) {
            // ── rollback on ANY failure ──
            try { c.rollback(); } catch (SQLException rb) { /* swallow rollback error */ }
            throw new RuntimeException("OrderDAO.save failed for orderId='" + order.getOrderId() + "': " + e.getMessage(), e);

        } finally {
            // Always restore auto-commit so the shared connection is not left in
            // manual-commit mode for the next caller (important for singleton conn)
            try { c.setAutoCommit(true); } catch (SQLException ex) { /* ignore */ }
        }
    }

    // ── SELECT by PK ────────────────────────────────────────────────────────

    /**
     * Finds an order by its UUID and eagerly loads its items.
     */
    public Optional<Order> findById(String orderId) {
        final String sql = "SELECT * FROM orders WHERE order_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Order order = mapOrderRow(rs);
                order.setItems(getItems(orderId));
                return Optional.of(order);
            }
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findById failed for orderId='" + orderId + "': " + e.getMessage(), e);
        }
    }

    // ── SELECT all orders for a user (order history) ─────────────────────────

    /**
     * Returns all orders for a user sorted newest-first.
     * Items list is NOT pre-loaded here to avoid N+1 queries on a potentially
     * long history — call {@link #getItems(String)} separately if needed.
     */
    public List<Order> findByUserId(int userId) {
        final String sql =
            "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC";
        List<Order> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrderRow(rs));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findByUserId failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── SELECT ALL (admin) ────────────────────────────────────────────────────

    /**
     * Returns all orders sorted newest-first (admin dashboard).
     * Items list is NOT pre-loaded.
     */
    public List<Order> findAll() {
        final String sql = "SELECT * FROM orders ORDER BY created_at DESC";
        List<Order> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapOrderRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    // ── UPDATE status (admin) ────────────────────────────────────────────────

    public void updateStatus(String orderId, OrderStatus status) {
        final String sql = "UPDATE orders SET status = ? WHERE order_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.updateStatus failed for orderId='" + orderId + "': " + e.getMessage(), e);
        }
    }

    // ── SELECT items for an order ────────────────────────────────────────────

    /**
     * Returns all {@link OrderItem}s for the given order, joining with
     * {@code products} so that each item's unit_price reflects the snapshot
     * stored in {@code order_items} (not the current live price).
     */
    public List<OrderItem> getItems(String orderId) {
        // JOIN is included for future use (e.g. product name in order history view)
        // but we map only the order_items columns to keep the model clean.
        final String sql =
            "SELECT oi.order_item_id, oi.order_id, oi.product_id, " +
            "       oi.quantity, oi.unit_price " +
            "FROM order_items oi " +
            "JOIN products p ON oi.product_id = p.product_id " +
            "WHERE oi.order_id = ?";

        List<OrderItem> items = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapOrderItemRow(rs));
                }
            }
            return items;

        } catch (SQLException e) {
            throw new RuntimeException("OrderDAO.getItems failed for orderId='" + orderId + "': " + e.getMessage(), e);
        }
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    /** Maps one row of {@code orders} to an {@link Order}. */
    private Order mapOrderRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getString("order_id"));
        o.setUserId(rs.getInt("user_id"));
        o.setStatus(OrderStatus.valueOf(rs.getString("status")));
        o.setTotalAmount(rs.getDouble("total_amount"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        o.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        o.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);

        return o;
    }

    /** Maps one row of {@code order_items} to an {@link OrderItem}. */
    private OrderItem mapOrderItemRow(ResultSet rs) throws SQLException {
        OrderItem item = new OrderItem();
        item.setOrderItemId(rs.getInt("order_item_id"));
        item.setOrderId(rs.getString("order_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        return item;
    }
}
