package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Payment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Accès à la table {@code payments} (KAN-7).
 */
public class PaymentDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public Optional<Payment> findByOrderId(String orderId) {
        final String sql = "SELECT * FROM payments WHERE order_id = ? LIMIT 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("PaymentDAO.findByOrderId failed: " + e.getMessage(), e);
        }
    }

    /**
     * Insère ou met à jour le paiement pour une commande (une ligne par {@code order_id} UNIQUE).
     */
    public int upsertPayment(String orderId,
                             Payment.Method method,
                             Payment.Status status,
                             double amount,
                             String transactionId,
                             LocalDateTime paidAt) {
        try {
            return upsertPayment(conn(), orderId, method, status, amount, transactionId, paidAt);
        } catch (SQLException e) {
            throw new RuntimeException("PaymentDAO.upsertPayment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Même logique que {@link #upsertPayment(String, Payment.Method, Payment.Status, double, String, LocalDateTime)}
     * sur une connexion existante (transaction KAN-19).
     */
    public int upsertPayment(Connection connection,
                             String orderId,
                             Payment.Method method,
                             Payment.Status status,
                             double amount,
                             String transactionId,
                             LocalDateTime paidAt) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        final String sql =
                "INSERT INTO payments (order_id, method, status, amount, transaction_id, paid_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "method = VALUES(method), " +
                "status = VALUES(status), " +
                "amount = VALUES(amount), " +
                "transaction_id = VALUES(transaction_id), " +
                "paid_at = VALUES(paid_at)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, method.name());
            ps.setString(3, status.name());
            ps.setDouble(4, amount);
            if (transactionId == null || transactionId.isBlank()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, transactionId);
            }
            if (paidAt == null) {
                ps.setNull(6, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(6, Timestamp.valueOf(paidAt));
            }
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT payment_id FROM payments WHERE order_id = ? LIMIT 1")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("payment_id") : 0;
            }
        }
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setOrderId(rs.getString("order_id"));
        p.setMethod(Payment.Method.valueOf(rs.getString("method")));
        p.setStatus(Payment.Status.valueOf(rs.getString("status")));
        p.setAmount(rs.getDouble("amount"));
        String tid = rs.getString("transaction_id");
        p.setTransactionId(rs.wasNull() ? null : tid);
        Timestamp ts = rs.getTimestamp("paid_at");
        p.setPaidAt(ts != null ? ts.toLocalDateTime() : null);
        return p;
    }
}
