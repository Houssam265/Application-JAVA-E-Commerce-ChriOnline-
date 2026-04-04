package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.ProductImage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductImageDAO {

    private java.sql.Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public List<ProductImage> findByProductId(int productId) {
        final String sql =
                "SELECT product_image_id, product_id, image_url, display_order, is_primary " +
                "FROM product_images WHERE product_id = ? " +
                "ORDER BY is_primary DESC, display_order ASC, product_image_id ASC";

        List<ProductImage> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ProductImageDAO.findByProductId failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    public void replaceForProduct(int productId, List<ProductImage> images) {
        final String deleteSql = "DELETE FROM product_images WHERE product_id = ?";
        final String insertSql =
                "INSERT INTO product_images (product_id, image_url, display_order, is_primary) " +
                "VALUES (?, ?, ?, ?)";

        try (PreparedStatement deletePs = conn().prepareStatement(deleteSql)) {
            deletePs.setInt(1, productId);
            deletePs.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ProductImageDAO.replaceForProduct delete failed for productId=" + productId + ": " + e.getMessage(), e);
        }

        if (images == null || images.isEmpty()) {
            return;
        }

        try (PreparedStatement insertPs = conn().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            for (ProductImage image : images) {
                insertPs.setInt(1, productId);
                insertPs.setString(2, image.getImageUrl());
                insertPs.setInt(3, image.getDisplayOrder());
                insertPs.setBoolean(4, image.isPrimary());
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("ProductImageDAO.replaceForProduct insert failed for productId=" + productId + ": " + e.getMessage(), e);
        }
    }

    private ProductImage mapRow(ResultSet rs) throws SQLException {
        ProductImage image = new ProductImage();
        image.setProductImageId(rs.getInt("product_image_id"));
        image.setProductId(rs.getInt("product_id"));
        image.setImageUrl(rs.getString("image_url"));
        image.setDisplayOrder(rs.getInt("display_order"));
        image.setPrimary(rs.getBoolean("is_primary"));
        return image;
    }
}
