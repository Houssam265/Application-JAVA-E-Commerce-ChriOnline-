package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code products} table.
 * All queries use PreparedStatement — no string concatenation.
 * All JDBC resources are closed via try-with-resources.
 */
public class ProductDAO {

    // ── Connection helper ───────────────────────────────────────────────────
    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── INSERT ──────────────────────────────────────────────────────────────

    /**
     * Persists a new product and returns the object with the generated
     * {@code product_id} populated.
     */
    public Product save(Product product) {
        final String sql =
            "INSERT INTO products (category_id, name, description, price, stock, image_url) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, product.getCategoryId());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setDouble(4, product.getPrice());
            ps.setInt(5, product.getStock());
            ps.setString(6, product.getImageUrl());
            ps.executeUpdate();

            try (ResultSet generated = ps.getGeneratedKeys()) {
                if (generated.next()) {
                    product.setProductId(generated.getInt(1));
                }
            }
            return product;

        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.save failed for product='" + product.getName() + "': " + e.getMessage(), e);
        }
    }

    // ── SELECT by PK ────────────────────────────────────────────────────────

    public Optional<Product> findById(int productId) {
        final String sql = "SELECT * FROM products WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.findById failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── SELECT ALL ───────────────────────────────────────────────────────────

    public List<Product> findAll() {
        final String sql = "SELECT * FROM products ORDER BY product_id ASC";
        List<Product> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    // ── SELECT by category ────────────────────────────────────────────────────

    public List<Product> findByCategoryId(int categoryId) {
        final String sql = "SELECT * FROM products WHERE category_id = ? ORDER BY product_id ASC";
        List<Product> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.findByCategoryId failed for categoryId=" + categoryId + ": " + e.getMessage(), e);
        }
    }

    // ── UPDATE all fields ─────────────────────────────────────────────────────

    public void update(Product product) {
        final String sql =
            "UPDATE products " +
            "SET category_id = ?, name = ?, description = ?, price = ?, stock = ?, image_url = ? " +
            "WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, product.getCategoryId());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setDouble(4, product.getPrice());
            ps.setInt(5, product.getStock());
            ps.setString(6, product.getImageUrl());
            ps.setInt(7, product.getProductId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.update failed for productId=" + product.getProductId() + ": " + e.getMessage(), e);
        }
    }

    // ── UPDATE stock only ─────────────────────────────────────────────────────

    /** Used by OrderService / CartService after a checkout to decrement stock. */
    public void updateStock(int productId, int newStock) {
        final String sql = "UPDATE products SET stock = ? WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.updateStock failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public void delete(int productId) {
        final String sql = "DELETE FROM products WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.delete failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── Stock availability check ─────────────────────────────────────────────

    /**
     * Returns {@code true} if the product exists and its stock >= requestedQty.
     * Called by CartService / OrderService before adding to cart or checking out.
     */
    public boolean isAvailable(int productId, int requestedQty) {
        final String sql = "SELECT stock FROM products WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("stock") >= requestedQty;
            }
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.isAvailable failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── Row mapper ───────────────────────────────────────────────────────────

    /** Maps one ResultSet row to a {@link Product}. Column names match the schema exactly. */
    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getDouble("price"));
        p.setStock(rs.getInt("stock"));
        p.setImageUrl(rs.getString("image_url"));
        return p;
    }
}
