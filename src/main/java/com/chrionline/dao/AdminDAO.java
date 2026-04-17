package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class AdminDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public Optional<Admin> findById(int adminId) {
        final String sql = "SELECT * FROM admin WHERE admin_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("AdminDAO.findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<Admin> findByUsername(String username) {
        final String sql = "SELECT * FROM admin WHERE username = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("AdminDAO.findByUsername failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    private Admin mapRow(ResultSet rs) throws SQLException {
        Admin admin = new Admin();
        admin.setAdminId(rs.getInt("admin_id"));
        admin.setUsername(rs.getString("username"));
        admin.setPublicKey(rs.getString("public_key"));
        admin.setActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            admin.setCreatedAt(createdAt.toLocalDateTime());
        }
        return admin;
    }
}
