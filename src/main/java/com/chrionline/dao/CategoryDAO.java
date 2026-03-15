package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code categories} table.
 * All queries use PreparedStatement — no string concatenation.
 * All JDBC resources are closed via try-with-resources.
 */
public class CategoryDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Finds a category by its primary key.
     */
    public Optional<Category> findById(int categoryId) {
        final String sql = "SELECT * FROM categories WHERE category_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("CategoryDAO.findById failed for categoryId=" + categoryId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns all categories ordered by category_id.
     */
    public List<Category> findAll() {
        final String sql = "SELECT * FROM categories ORDER BY category_id ASC";
        List<Category> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("CategoryDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setCategoryId(rs.getInt("category_id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        return c;
    }
}
