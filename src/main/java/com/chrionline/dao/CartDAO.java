package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Cart;
import com.chrionline.model.CartItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code carts} and {@code cart_items} tables.
 *
 * All queries use PreparedStatement — no string concatenation.
 * All JDBC resources are closed via try-with-resources.
 */
public class CartDAO {

    // ── Connection helper ───────────────────────────────────────────────────
    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── CREATE cart for user ─────────────────────────────────────────────────

    /**
     * Inserts a new row into {@code carts} for the given user and returns the
     * resulting {@link Cart} with the generated {@code cart_id} set.
     *
     * The schema enforces UNIQUE on {@code user_id}, so calling this more than
     * once per user will throw. CartService must check first.
     */
    public Cart createForUser(int userId) {
        final String sql = "INSERT INTO carts (user_id) VALUES (?)";

        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.executeUpdate();

            try (ResultSet generated = ps.getGeneratedKeys()) {
                if (generated.next()) {
                    int cartId = generated.getInt(1);
                    // Fetch the full row so created_at / updated_at are server-generated
                    return findByCartId(cartId)
                           .orElseThrow(() -> new RuntimeException("CartDAO.createForUser — cart not found after insert"));
                }
            }
            throw new RuntimeException("CartDAO.createForUser — no generated key returned for userId=" + userId);

        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.createForUser failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── SELECT cart + items by userId ────────────────────────────────────────

    /**
     * Finds the cart for a user and eagerly loads all its {@link CartItem}s.
     */
    public Optional<Cart> findByUserId(int userId) {
        final String sql = "SELECT * FROM carts WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Cart cart = mapCartRow(rs);
                cart.setItems(loadItems(cart.getCartId()));
                return Optional.of(cart);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.findByUserId failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── ADD / UPDATE item ────────────────────────────────────────────────────

    /**
     * Inserts a new row into {@code cart_items}. If the product already exists in
     * the cart (violating the UNIQUE KEY {@code uq_cart_product}), the existing
     * row's quantity is incremented by {@code quantity} instead.
     *
     * <p>Uses INSERT … ON DUPLICATE KEY UPDATE to exploit the composite unique
     * index defined in the schema — a single round-trip to the database.</p>
     */
    public void addItem(int cartId, int productId, int quantity, double unitPrice) {
        final String sql =
            "INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) " +
            "VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setDouble(4, unitPrice);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.addItem failed for cartId=" + cartId + ", productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── UPDATE item quantity ─────────────────────────────────────────────────

    public void updateItemQuantity(int cartItemId, int newQuantity) {
        final String sql = "UPDATE cart_items SET quantity = ? WHERE cart_item_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setInt(2, cartItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.updateItemQuantity failed for cartItemId=" + cartItemId + ": " + e.getMessage(), e);
        }
    }

    // ── REMOVE single item ────────────────────────────────────────────────────

    public void removeItem(int cartItemId) {
        final String sql = "DELETE FROM cart_items WHERE cart_item_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cartItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.removeItem failed for cartItemId=" + cartItemId + ": " + e.getMessage(), e);
        }
    }

    // ── CLEAR all items (post-checkout) ──────────────────────────────────────

    public void clearCart(int cartId) {
        final String sql = "DELETE FROM cart_items WHERE cart_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.clearCart failed for cartId=" + cartId + ": " + e.getMessage(), e);
        }
    }

    // ── Internal: find by cart PK ─────────────────────────────────────────────

    private Optional<Cart> findByCartId(int cartId) {
        final String sql = "SELECT * FROM carts WHERE cart_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cartId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCartRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("CartDAO.findByCartId failed for cartId=" + cartId + ": " + e.getMessage(), e);
        }
    }

    // ── Internal: load all items for a cart ───────────────────────────────────

    private List<CartItem> loadItems(int cartId) throws SQLException {
        final String sql = "SELECT * FROM cart_items WHERE cart_id = ?";
        List<CartItem> items = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapCartItemRow(rs));
                }
            }
        }
        return items;
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    /** Maps one row of {@code carts} to a {@link Cart}. */
    private Cart mapCartRow(ResultSet rs) throws SQLException {
        Cart c = new Cart();
        c.setCartId(rs.getInt("cart_id"));
        c.setUserId(rs.getInt("user_id"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        c.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        c.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);

        return c;
    }

    /** Maps one row of {@code cart_items} to a {@link CartItem}. */
    private CartItem mapCartItemRow(ResultSet rs) throws SQLException {
        CartItem item = new CartItem();
        item.setCartItemId(rs.getInt("cart_item_id"));
        item.setCartId(rs.getInt("cart_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        return item;
    }
}
