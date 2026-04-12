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

    public static final class LoginSecurityState {
        private final int failures;
        private final long blockedUntilMs;
        private final long ipBlockedUntilMs;

        public LoginSecurityState(int failures, long blockedUntilMs, long ipBlockedUntilMs) {
            this.failures = failures;
            this.blockedUntilMs = blockedUntilMs;
            this.ipBlockedUntilMs = ipBlockedUntilMs;
        }

        public int getFailures() {
            return failures;
        }

        public long getBlockedUntilMs() {
            return blockedUntilMs;
        }

        public long getIpBlockedUntilMs() {
            return ipBlockedUntilMs;
        }
    }

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

    public void ensurePasswordResetSchema() {
        ensureColumn("password_reset_token",
                "ALTER TABLE users ADD COLUMN password_reset_token VARCHAR(64) NULL");
        ensureColumn("password_reset_expires_at",
                "ALTER TABLE users ADD COLUMN password_reset_expires_at DATETIME NULL");
    }

    public void ensureLoginSecuritySchema() {
        final String sql = """
            CREATE TABLE IF NOT EXISTS login_security (
                email VARCHAR(150) NOT NULL,
                ip_address VARCHAR(64) NOT NULL,
                failures INT NOT NULL DEFAULT 0,
                window_started_at DATETIME NULL,
                blocked_until DATETIME NULL,
                ip_blocked_until DATETIME NULL,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (email, ip_address),
                INDEX idx_login_security_ip (ip_address),
                INDEX idx_login_security_ip_blocked (ip_address, ip_blocked_until)
            )
            """;
        try (Statement st = conn().createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.ensureLoginSecuritySchema failed: " + e.getMessage(), e);
        }
    }

    public User save(User user) {
        final String sql = """
            INSERT INTO users (
                username, email, password_hash, role, is_suspended,
                is_email_verified, email_verification_code,
                email_verification_expires_at, email_verification_sent_at,
                trusted_login_ip, login_ip_verification_code,
                login_ip_verification_expires_at, login_ip_verification_sent_at,
                login_ip_verification_pending_ip, password_reset_token, password_reset_expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setString(15, user.getPasswordResetToken());
            setTimestamp(ps, 16, user.getPasswordResetExpiresAt());
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

    public Optional<User> findByPasswordResetToken(String token) {
        return findOne("SELECT * FROM users WHERE password_reset_token = ?", ps -> ps.setString(1, token),
                "UserDAO.findByPasswordResetToken failed for token='" + token + "': ");
    }

    public void updatePassword(int userId, String newPasswordHash) {
        final String sql = "UPDATE users SET password_hash = ?, password_reset_token = NULL, password_reset_expires_at = NULL WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.updatePassword failed for userId=" + userId + ": " + e.getMessage(), e);
        }
    }

    public void updatePasswordResetChallenge(int userId, String token, LocalDateTime expiresAt) {
        final String sql = "UPDATE users SET password_reset_token = ?, password_reset_expires_at = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, token);
            setTimestamp(ps, 2, expiresAt);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.updatePasswordResetChallenge failed for userId=" + userId + ": " + e.getMessage(), e);
        }
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
                   login_ip_verification_pending_ip = ?,
                   password_reset_token = ?,
                   password_reset_expires_at = ?
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
            ps.setString(12, user.getPasswordResetToken());
            setTimestamp(ps, 13, user.getPasswordResetExpiresAt());
            ps.setInt(14, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.update failed for userId=" + user.getUserId() + ": " + e.getMessage(), e);
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

    public synchronized LoginSecurityState getLoginSecurityState(String email, String ipAddress, long nowMs) {
        final String sql = """
            SELECT failures, blocked_until, ip_blocked_until
              FROM login_security
             WHERE email = ? AND ip_address = ?
             LIMIT 1
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, ipAddress);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new LoginSecurityState(0, 0L, getIpBlockedUntilMs(ipAddress));
                }
                long blockedUntilMs = toEpochMillis(rs.getTimestamp("blocked_until"));
                long ipBlockedUntilMs = toEpochMillis(rs.getTimestamp("ip_blocked_until"));
                if (blockedUntilMs <= nowMs) {
                    blockedUntilMs = 0L;
                }
                if (ipBlockedUntilMs <= nowMs) {
                    ipBlockedUntilMs = 0L;
                }
                return new LoginSecurityState(rs.getInt("failures"), blockedUntilMs, ipBlockedUntilMs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.getLoginSecurityState failed for email='" + email + "', ip='" + ipAddress + "': " + e.getMessage(), e);
        }
    }

    public synchronized long getIpBlockedUntilMs(String ipAddress) {
        final String sql = """
            SELECT MAX(ip_blocked_until) AS ip_blocked_until
              FROM login_security
             WHERE ip_address = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return toEpochMillis(rs.getTimestamp("ip_blocked_until"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.getIpBlockedUntilMs failed for ip='" + ipAddress + "': " + e.getMessage(), e);
        }
    }

    public synchronized LoginSecurityState registerFailedLoginAttempt(String email,
                                                                      String ipAddress,
                                                                      long nowMs,
                                                                      long loginWindowMs,
                                                                      int maxLoginAttempts) {
        final String selectSql = """
            SELECT failures, window_started_at
              FROM login_security
             WHERE email = ? AND ip_address = ?
             LIMIT 1
            """;
        final String insertSql = """
            INSERT INTO login_security (
                email, ip_address, failures, window_started_at, blocked_until, ip_blocked_until
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
        final String updateSql = """
            UPDATE login_security
               SET failures = ?,
                   window_started_at = ?,
                   blocked_until = ?,
                   ip_blocked_until = ?
             WHERE email = ? AND ip_address = ?
            """;

        try {
            int failures = 0;
            LocalDateTime windowStartedAt = LocalDateTime.now();
            boolean exists = false;

            try (PreparedStatement ps = conn().prepareStatement(selectSql)) {
                ps.setString(1, email);
                ps.setString(2, ipAddress);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                        failures = rs.getInt("failures");
                        Timestamp windowTs = rs.getTimestamp("window_started_at");
                        if (windowTs != null) {
                            windowStartedAt = windowTs.toLocalDateTime();
                        }
                    }
                }
            }

            long windowStartedMs = Timestamp.valueOf(windowStartedAt).getTime();
            if (!exists || nowMs - windowStartedMs > loginWindowMs) {
                failures = 0;
                windowStartedAt = LocalDateTime.now();
            }

            failures++;
            LocalDateTime blockedUntil = null;
            LocalDateTime ipBlockedUntil = null;
            if (failures >= maxLoginAttempts) {
                ipBlockedUntil = LocalDateTime.now().plusHours(24);
                blockedUntil = ipBlockedUntil;
            } else if (failures >= 9) {
                blockedUntil = LocalDateTime.now().plusHours(1);
            } else if (failures >= 6) {
                blockedUntil = LocalDateTime.now().plusMinutes(10);
            } else if (failures >= 3) {
                blockedUntil = LocalDateTime.now().plusMinutes(1);
            }

            if (exists) {
                try (PreparedStatement ps = conn().prepareStatement(updateSql)) {
                    ps.setInt(1, failures);
                    setTimestamp(ps, 2, windowStartedAt);
                    setTimestamp(ps, 3, blockedUntil);
                    setTimestamp(ps, 4, ipBlockedUntil);
                    ps.setString(5, email);
                    ps.setString(6, ipAddress);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn().prepareStatement(insertSql)) {
                    ps.setString(1, email);
                    ps.setString(2, ipAddress);
                    ps.setInt(3, failures);
                    setTimestamp(ps, 4, windowStartedAt);
                    setTimestamp(ps, 5, blockedUntil);
                    setTimestamp(ps, 6, ipBlockedUntil);
                    ps.executeUpdate();
                }
            }
            return new LoginSecurityState(
                failures,
                blockedUntil != null ? Timestamp.valueOf(blockedUntil).getTime() : 0L,
                ipBlockedUntil != null ? Timestamp.valueOf(ipBlockedUntil).getTime() : 0L
            );
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.registerFailedLoginAttempt failed for email='" + email + "', ip='" + ipAddress + "': " + e.getMessage(), e);
        }
    }

    public synchronized void clearLoginSecurityState(String email, String ipAddress) {
        final String sql = "DELETE FROM login_security WHERE email = ? AND ip_address = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, ipAddress);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("UserDAO.clearLoginSecurityState failed for email='" + email + "', ip='" + ipAddress + "': " + e.getMessage(), e);
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

    private long toEpochMillis(Timestamp timestamp) {
        return timestamp != null ? timestamp.getTime() : 0L;
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
        u.setPasswordResetToken(getStringOrNull(rs, "password_reset_token"));
        u.setPasswordResetExpiresAt(getLocalDateTimeOrNull(rs, "password_reset_expires_at"));
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
