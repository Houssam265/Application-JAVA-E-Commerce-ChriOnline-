package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Product;
import com.chrionline.model.ProductImage;

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

    private final ProductImageDAO productImageDAO = new ProductImageDAO();

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
            "INSERT INTO products (category_id, name, description, price, stock, is_available, image_url) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, product.getCategoryId());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setDouble(4, product.getPrice());
            ps.setInt(5, product.getStock());
            ps.setBoolean(6, product.getStock() > 0 && product.isAvailable());
            ps.setString(7, product.getImageUrl());
            ps.executeUpdate();

            try (ResultSet generated = ps.getGeneratedKeys()) {
                if (generated.next()) {
                    product.setProductId(generated.getInt(1));
                }
            }
            persistImages(product);
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

    /**
     * Catalogue client : produits disponibles à la vente (KAN-19).
     */
    public List<Product> findAllAvailable() {
        final String sql = "SELECT * FROM products WHERE is_available = TRUE ORDER BY product_id ASC";
        return queryAllProducts(sql, null);
    }

    public List<Product> findByCategoryIdAvailable(int categoryId) {
        final String sql = "SELECT * FROM products WHERE category_id = ? AND is_available = TRUE ORDER BY product_id ASC";
        return queryAllProducts(sql, categoryId);
    }

    /** Tous les produits (y compris indisponibles) — admin / rapports. */
    public List<Product> findAll() {
        final String sql = "SELECT * FROM products ORDER BY product_id ASC";
        return queryAllProducts(sql, null);
    }

    public List<Product> findTopSellingAvailable(int limit) {
        final String sql = """
            SELECT p.*
              FROM products p
              JOIN order_items oi ON oi.product_id = p.product_id
              JOIN orders o ON o.order_id = oi.order_id
             WHERE p.is_available = TRUE
               AND o.status IN ('VALIDATED', 'SHIPPED', 'DELIVERED')
             GROUP BY p.product_id, p.category_id, p.name, p.description, p.price, p.stock, p.is_available, p.image_url
             ORDER BY SUM(oi.quantity) DESC, p.product_id ASC
             LIMIT ?
            """;

        List<Product> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.findTopSellingAvailable failed: " + e.getMessage(), e);
        }
    }

    public List<Product> findRecentAvailable(int limit) {
        final String sql = """
            SELECT *
              FROM products
             WHERE is_available = TRUE
             ORDER BY product_id DESC
             LIMIT ?
            """;

        List<Product> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.findRecentAvailable failed: " + e.getMessage(), e);
        }
    }

    private List<Product> queryAllProducts(String sql, Integer categoryId) {
        List<Product> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (categoryId != null) {
                ps.setInt(1, categoryId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO query failed: " + e.getMessage(), e);
        }
    }

    // ── SELECT by category ────────────────────────────────────────────────────

    public List<Product> findByCategoryId(int categoryId) {
        final String sql = "SELECT * FROM products WHERE category_id = ? ORDER BY product_id ASC";
        return queryAllProducts(sql, categoryId);
    }

    // ── UPDATE all fields ─────────────────────────────────────────────────────

    public void update(Product product) {
        final String sql =
            "UPDATE products " +
            "SET category_id = ?, name = ?, description = ?, price = ?, stock = ?, is_available = ?, image_url = ? " +
            "WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, product.getCategoryId());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setDouble(4, product.getPrice());
            ps.setInt(5, product.getStock());
            ps.setBoolean(6, product.getStock() > 0 && product.isAvailable());
            ps.setString(7, product.getImageUrl());
            ps.setInt(8, product.getProductId());
            ps.executeUpdate();
            persistImages(product);
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.update failed for productId=" + product.getProductId() + ": " + e.getMessage(), e);
        }
    }

    // ── UPDATE stock only ─────────────────────────────────────────────────────

    /** Used by OrderService / CartService after a checkout to decrement stock. */
    public void updateStock(int productId, int newStock) {
        final String sql = "UPDATE products SET stock = ?, is_available = ? WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setBoolean(2, newStock > 0);
            ps.setInt(3, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.updateStock failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public void updateAvailability(int productId, boolean available) {
        final String sql = "UPDATE products SET is_available = ? WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setBoolean(1, available);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductDAO.updateAvailability failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    // ── Stock availability check ─────────────────────────────────────────────

    /**
     * Returns {@code true} if the product exists and its stock >= requestedQty.
     * Called by CartService / OrderService before adding to cart or checking out.
     */
    public boolean isAvailable(int productId, int requestedQty) {
        final String sql = "SELECT stock, is_available FROM products WHERE product_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("is_available") && rs.getInt("stock") >= requestedQty;
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
        p.setAvailable(rs.getBoolean("is_available"));
        p.setImageUrl(rs.getString("image_url"));
        List<ProductImage> images = productImageDAO.findByProductId(p.getProductId());
        if (!images.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (ProductImage image : images) {
                urls.add(image.getImageUrl());
                if (image.isPrimary()) {
                    p.setImageUrl(image.getImageUrl());
                }
            }
            p.setImageUrls(urls);
        } else if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
            p.setImageUrls(List.of(p.getImageUrl()));
        }
        return p;
    }

    private void persistImages(Product product) {
        if (product.getProductId() <= 0) return;

        List<String> urls = product.getImageUrls();
        if ((urls == null || urls.isEmpty()) && product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            urls = List.of(product.getImageUrl());
        }

        List<ProductImage> images = new ArrayList<>();
        if (urls != null) {
            String primary = product.getImageUrl();
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                if (url == null || url.isBlank()) continue;
                ProductImage image = new ProductImage();
                image.setProductId(product.getProductId());
                image.setImageUrl(url);
                image.setDisplayOrder(i);
                image.setPrimary(primary != null && primary.equals(url));
                images.add(image);
            }
            if (!images.isEmpty() && images.stream().noneMatch(ProductImage::isPrimary)) {
                images.get(0).setPrimary(true);
                product.setImageUrl(images.get(0).getImageUrl());
            }
        }

        productImageDAO.replaceForProduct(product.getProductId(), images);
    }
}
