package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code users} table.
 */
public class UserDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public void ensureEmailVerificationSchema() {
        boolean columnAdded = false;
        columnAdded |= ensureColumn("is_email_verified",
                "ALTER TABLE users ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT FALSE");
        columnAdded |= ensureColumn("email_verification_code",
                "ALTER TABLE users ADD COLUMN email_verification_code VARCHAR(16) NULL");
        columnAdded |= ensureColumn("email_verification_expires_at",
                "ALTER TABLE users ADD COLUMN email_verification_expires_at DATETIME NULL");
        columnAdded |= ensureColumn("email_verification_sent_at",
                "ALTER TABLE users ADD COLUMN email_verification_sent_at DATETIME NULL");

        if (columnAdded) {
            try (PreparedStatement ps = conn().prepareStatement(
                    "UPDATE users SET is_email_verified = TRUE WHERE is_email_verified = FALSE")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("UserDAO.ensureEmailVerificationSchema failed during backfill: " + e.getMessage(), e);
            }
        }
    }

    public void ensureLoginIpVerificationSchema() {
        ensureColumn("trusted_login_ip",
                "ALTER TABLE users ADD COLUMN trusted_login_ip VARCHAR(64) NULL");
        ensureColumn("login_ip_verification_code",
                "ALTER TABLE users ADD COLUMN login_ip_verification_code VARCHAR(16) NULL");
        ensureColumn("login_ip_verification_expires_at",
                "ALTER TABLE users ADD COLUMN login_ip_verification_expires_at DATETIME NULL");
        ensureColumn("login_ip_verification_sent_at",
                "ALTER TABLE users ADD COLUMN login_ip_verification_sent_at DATETIME NULL");
        ensureColumn("login_ip_verification_pending_ip",
                "ALTER TABLE users ADD COLUMN login_ip_verification_pending_ip VARCHAR(64) NULL");
    }

    public User save(User user) {
        final String sql = """
            INSERT INTO users (
                username, email, password_hash, role, is_suspended,
                is_email_verified, email_verification_code,
                email_verification_expires_at, email_verification_sent_at,
                trusted_login_ip, login_ip_verification_code,
                login_ip_verification_expires_at, login_ip_verification_sent_at,
                login_ip_verification_pending_ip
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setBoolean(5, user.isSuspended());
            ps.setBoolean(6, user.isEmailVerified());
            ps.setString(7, user.getEmailVerificationCode());
            setTimestamp(ps, 8, user.getEmailVerificationExpiresAt());
            setTimestamp(ps, 9, user.getEmailVerificationSentAt());
            ps.setString(10, user.getTrustedLoginIp());
            ps.setString(11, user.getLoginIpVerificationCode());
            setTimestamp(ps, 12, user.getLoginIpVerificationExpiresAt());
            setTimestamp(ps, 13, user.getLoginIpVerificationSentAt());
            ps.setString(14, user.getLoginIpVerificationPendingIp());
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

    public Optional<User> findById(int userId) {
        return findOne("SELECT * FROM users WHERE user_id = ?", ps -> ps.setInt(1, userId),
                "UserDAO.findById failed for userId=" + userId + ": ");
    }

    public Optional<User> findByEmail(String email) {
        return findOne("SELECT * FROM users WHERE email = ?", ps -> ps.setString(1, email),
                "UserDAO.findByEmail failed for email='" + email + "': ");
    }

    public Optional<User> findByUsername(String username) {
        return findOne("SELECT * FROM users WHERE username = ?", ps -> ps.setString(1, username),
                "UserDAO.findByUsername failed for username='" + username + "': ");
    }

    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM users WHERE email = ? LIMIT 1", email,
                "UserDAO.existsByEmail failed for email='" + email + "': ");
    }

    public boolean existsByUsername(String username) {
        return exists("SELECT 1 FROM users WHERE username = ? LIMIT 1", username,
                "UserDAO.existsByUsername failed for username='" + username + "': ");
    }

    public void update(User user) {
        final String sql = """
            UPDATE users
               SET username = ?,
                   email = ?,
                   is_email_verified = ?,
                   email_verification_code = ?,
                   email_verification_expires_at = ?,
                   email_verification_sent_at = ?,
                   trusted_login_ip = ?,
                   login_ip_verification_code = ?,
                   login_ip_verification_expires_at = ?,
                   login_ip_verification_sent_at = ?,
                   login_ip_verification_pending_ip = ?
             WHERE user_id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setBoolean(3, user.isEmailVerified());
            ps.setString(4, user.getEmailVerificationCode());
            setTimestamp(ps, 5, user.getEmailVerificationExpiresAt());
            setTimestamp(ps, 6, user.getEmailVerificationSentAt());
            ps.setString(7, user.getTrustedLoginIp());
            ps.setString(8, user.getLoginIpVerificationCode());
            setTimestamp(ps, 9, user.getLoginIpVerificationExpiresAt());
            setTimestamp(ps, 10, user.getLoginIpVerificationSentAt());
            ps.setString(11, user.getLoginIpVerificationPendingIp());
            ps.setInt(12, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.update failed for userId=" + user.getUserId() + ": " + e.getMessage(), e);
        }
    }

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

    public void updateVerificationChallenge(int userId, String code, LocalDateTime expiresAt, LocalDateTime sentAt) {
        final String sql = """
            UPDATE users
               SET email_verification_code = ?,
                   email_verification_expires_at = ?,
                   email_verification_sent_at = ?,
                   is_email_verified = FALSE
             WHERE user_id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, code);
            setTimestamp(ps, 2, expiresAt);
            setTimestamp(ps, 3, sentAt);
            ps.setInt(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.updateVerificationChallenge failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public void markEmailVerified(int userId) {
        final String sql = """
            UPDATE users
               SET is_email_verified = TRUE,
                   email_verification_code = NULL,
                   email_verification_expires_at = NULL,
                   email_verification_sent_at = NULL
             WHERE user_id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.markEmailVerified failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public void updateLoginIpVerificationChallenge(int userId, String code, LocalDateTime expiresAt,
                                                   LocalDateTime sentAt, String pendingIp) {
        final String sql = """
            UPDATE users
               SET login_ip_verification_code = ?,
                   login_ip_verification_expires_at = ?,
                   login_ip_verification_sent_at = ?,
                   login_ip_verification_pending_ip = ?
             WHERE user_id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, code);
            setTimestamp(ps, 2, expiresAt);
            setTimestamp(ps, 3, sentAt);
            ps.setString(4, pendingIp);
            ps.setInt(5, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.updateLoginIpVerificationChallenge failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public void trustLoginIp(int userId, String trustedIp) {
        final String sql = """
            UPDATE users
               SET trusted_login_ip = ?,
                   login_ip_verification_code = NULL,
                   login_ip_verification_expires_at = NULL,
                   login_ip_verification_sent_at = NULL,
                   login_ip_verification_pending_ip = NULL
             WHERE user_id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, trustedIp);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.trustLoginIp failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public void delete(int userId) {
        final String sql = "DELETE FROM users WHERE user_id = ?";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.delete failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

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

    public void setSuspended(int userId, boolean suspended) {
        final String sql = "UPDATE users SET is_suspended = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setBoolean(1, suspended);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.setSuspended failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    private Optional<User> findOne(String sql, SqlConsumer<PreparedStatement> binder, String errorPrefix) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            binder.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(errorPrefix + e.getMessage(), e);
        }
    }

    private boolean exists(String sql, String value, String errorPrefix) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(errorPrefix + e.getMessage(), e);
        }
    }

    private boolean ensureColumn(String columnName, String alterSql) {
        final String sql = """
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'users'
               AND column_name = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.ensureColumn failed for " + columnName + ": " + e.getMessage(), e);
        }

        try (Statement st = conn().createStatement()) {
            st.executeUpdate(alterSql);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.ensureColumn alter failed for " + columnName + ": " + e.getMessage(), e);
        }
    }

    private void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setTimestamp(index, null);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(User.Role.valueOf(rs.getString("role")));
        u.setSuspended(getBooleanOrDefault(rs, "is_suspended", false));
        u.setEmailVerified(getBooleanOrDefault(rs, "is_email_verified", true));
        u.setEmailVerificationCode(getStringOrNull(rs, "email_verification_code"));
        u.setEmailVerificationExpiresAt(getLocalDateTimeOrNull(rs, "email_verification_expires_at"));
        u.setEmailVerificationSentAt(getLocalDateTimeOrNull(rs, "email_verification_sent_at"));
        u.setTrustedLoginIp(getStringOrNull(rs, "trusted_login_ip"));
        u.setLoginIpVerificationCode(getStringOrNull(rs, "login_ip_verification_code"));
        u.setLoginIpVerificationExpiresAt(getLocalDateTimeOrNull(rs, "login_ip_verification_expires_at"));
        u.setLoginIpVerificationSentAt(getLocalDateTimeOrNull(rs, "login_ip_verification_sent_at"));
        u.setLoginIpVerificationPendingIp(getStringOrNull(rs, "login_ip_verification_pending_ip"));
        u.setCreatedAt(getLocalDateTimeOrNull(rs, "created_at"));
        return u;
    }

    private boolean getBooleanOrDefault(ResultSet rs, String column, boolean defaultValue) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException ignored) {
            return defaultValue;
        }
    }

    private String getStringOrNull(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private LocalDateTime getLocalDateTimeOrNull(ResultSet rs, String column) {
        try {
            Timestamp ts = rs.getTimestamp(column);
            return ts != null ? ts.toLocalDateTime() : null;
        } catch (SQLException ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }
}
