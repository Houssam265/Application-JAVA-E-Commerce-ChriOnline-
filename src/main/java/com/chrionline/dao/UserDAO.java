package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code users} table.
 * All queries use PreparedStatement — no string concatenation.
 * All JDBC resources are closed via try-with-resources.
 */
public class UserDAO {

    // ── Connection helper ───────────────────────────────────────────────────
    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── INSERT ──────────────────────────────────────────────────────────────

    /**
     * Persists a new user and returns the same object with the generated
     * {@code user_id} set.
     *
     * @param user user to insert (userId field is ignored — DB auto-increments it)
     * @return the user with userId populated
     */
    public User save(User user) {
        final String sql =
            "INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.executeUpdate();

            try (ResultSet generated = ps.getGeneratedKeys()) {
                if (generated.next()) {
                    user.setUserId(generated.getInt(1));
                }
            }
            return user;

        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.save failed for username='" + user.getUsername() + "': " + e.getMessage(), e);
        }
    }

    // ── SELECT by PK ────────────────────────────────────────────────────────

    public Optional<User> findById(int userId) {
        final String sql = "SELECT * FROM users WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.findById failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── SELECT by email (login) ──────────────────────────────────────────────

    public Optional<User> findByEmail(String email) {
        final String sql = "SELECT * FROM users WHERE email = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.findByEmail failed for email='" + email + "': " + e.getMessage(), e);
        }
    }

    // ── SELECT by username (registration duplicate check) ───────────────────

    public Optional<User> findByUsername(String username) {
        final String sql = "SELECT * FROM users WHERE username = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.findByUsername failed for username='" + username + "': " + e.getMessage(), e);
        }
    }

    // ── Existence checks ─────────────────────────────────────────────────────

    public boolean existsByEmail(String email) {
        final String sql = "SELECT 1 FROM users WHERE email = ? LIMIT 1";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.existsByEmail failed for email='" + email + "': " + e.getMessage(), e);
        }
    }

    public boolean existsByUsername(String username) {
        final String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.existsByUsername failed for username='" + username + "': " + e.getMessage(), e);
        }
    }

    // ── UPDATE (profile fields) ──────────────────────────────────────────────

    /** Updates {@code username} and {@code email} only. */
    public void update(User user) {
        final String sql = "UPDATE users SET username = ?, email = ? WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setInt(3, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.update failed for userId=" + user.getUserId() + ": " + e.getMessage(), e);
        }
    }

    /** Updates the stored BCrypt hash — used by AuthService after password change. */
    public void updatePassword(int userId, String newPasswordHash) {
        final String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.updatePassword failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public void delete(int userId) {
        final String sql = "DELETE FROM users WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.delete failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    // ── SELECT ALL (admin panel) ─────────────────────────────────────────────

    public List<User> findAll() {
        final String sql = "SELECT * FROM users ORDER BY user_id ASC";
        List<User> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.findAll failed: " + e.getMessage(), e);
        }
    }

    // ── SELECT by role (admin dashboard) ─────────────────────────────────────

    /**
     * Returns all users with the given role.
     * <p>
     * Binds via {@link User.Role#name()} so the string matches the DB ENUM
     * exactly (e.g. {@code "CLIENT"} or {@code "ADMIN"}).
     *
     * @param role the role to filter by
     * @return list of matching users, empty if none
     */
    public List<User> findByRole(User.Role role) {
        final String sql = "SELECT * FROM users WHERE role = ? ORDER BY user_id ASC";
        List<User> result = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.findByRole failed for role=" + role + ": " + e.getMessage(), e);
        }
    }

    // ── Row mapper ───────────────────────────────────────────────────────────

    /** Maps one ResultSet row to a {@link User}. Column names match the schema exactly. */
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(User.Role.valueOf(rs.getString("role")));

        Timestamp ts = rs.getTimestamp("created_at");
        u.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);

        return u;
    }
}
