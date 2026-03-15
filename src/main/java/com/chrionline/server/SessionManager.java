package com.chrionline.server;

import com.chrionline.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Validates session tokens against the {@code sessions} table.
 * Used by ClientHandler to allow or deny protected actions (catalogue, cart, etc.).
 */
public class SessionManager {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Returns true if the token exists, is active and not expired.
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        final String sql =
            "SELECT 1 FROM sessions WHERE token = ? AND is_active = TRUE AND expires_at > CURRENT_TIMESTAMP LIMIT 1";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("SessionManager.isTokenValid failed: " + e.getMessage(), e);
        }
    }
}
