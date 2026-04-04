package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.ServerLog;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LogDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public void save(ServerLog log) {
        final String sql =
                "INSERT INTO server_logs (user_id, action, status, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getUserId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, log.getUserId());
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getStatus());
            if (log.getMessage() == null || log.getMessage().isBlank()) {
                ps.setNull(4, Types.LONGVARCHAR);
            } else {
                ps.setString(4, log.getMessage());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setLogId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("LogDAO.save failed: " + e.getMessage(), e);
        }
    }

    public List<ServerLog> findAll() {
        final String sql = "SELECT * FROM server_logs ORDER BY created_at DESC";
        List<ServerLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("LogDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    public List<ServerLog> findByUserId(int userId) {
        final String sql = "SELECT * FROM server_logs WHERE user_id = ? ORDER BY created_at DESC";
        List<ServerLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("LogDAO.findByUserId failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public List<ServerLog> findByStatus(String status) {
        final String sql = "SELECT * FROM server_logs WHERE status = ? ORDER BY created_at DESC";
        List<ServerLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("LogDAO.findByStatus failed for status=" + status + ": " + e.getMessage(), e);
        }
    }

    public List<ServerLog> findRecent(int limit) {
        final String sql = "SELECT * FROM server_logs ORDER BY created_at DESC LIMIT ?";
        List<ServerLog> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("LogDAO.findRecent failed: " + e.getMessage(), e);
        }
    }

    private ServerLog mapRow(ResultSet rs) throws SQLException {
        ServerLog log = new ServerLog();
        log.setLogId(rs.getInt("log_id"));
        int uid = rs.getInt("user_id");
        if (rs.wasNull()) {
            log.setUserId(null);
        } else {
            log.setUserId(uid);
        }
        log.setAction(rs.getString("action"));
        log.setStatus(rs.getString("status"));
        String msg = rs.getString("message");
        if (rs.wasNull()) {
            msg = null;
        }
        log.setMessage(msg);
        try {
            LocalDateTime t = rs.getObject("created_at", LocalDateTime.class);
            log.setCreatedAt(t);
        } catch (Exception ex) {
            Timestamp ts = rs.getTimestamp("created_at");
            log.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        }
        return log;
    }
}

