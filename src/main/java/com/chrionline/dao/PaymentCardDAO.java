package com.chrionline.dao;

import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.PaymentCard;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentCardDAO {
    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    public List<PaymentCard> findByUserId(int userId) {
        final String sql = "SELECT * FROM payment_cards WHERE user_id = ? ORDER BY created_at DESC, card_id DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PaymentCard> cards = new ArrayList<>();
                while (rs.next()) cards.add(mapRow(rs));
                return cards;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PaymentCardDAO.findByUserId failed: " + e.getMessage(), e);
        }
    }

    public Optional<PaymentCard> findByIdForUser(int cardId, int userId) {
        final String sql = "SELECT * FROM payment_cards WHERE card_id = ? AND user_id = ? LIMIT 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cardId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PaymentCardDAO.findByIdForUser failed: " + e.getMessage(), e);
        }
    }

    public int save(int userId, String brand, String last4, String expiry, String encryptedCardNumber, String cardIv) {
        final String sql = """
                INSERT INTO payment_cards (user_id, brand, last4, expiry, encrypted_card_number, card_iv)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    brand = VALUES(brand),
                    expiry = VALUES(expiry),
                    encrypted_card_number = VALUES(encrypted_card_number),
                    card_iv = VALUES(card_iv)
                """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, brand);
            ps.setString(3, last4);
            ps.setString(4, expiry);
            ps.setString(5, encryptedCardNumber);
            ps.setString(6, cardIv);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            try (PreparedStatement find = conn().prepareStatement(
                    "SELECT card_id FROM payment_cards WHERE user_id = ? AND last4 = ? AND expiry = ? LIMIT 1")) {
                find.setInt(1, userId);
                find.setString(2, last4);
                find.setString(3, expiry);
                try (ResultSet rs = find.executeQuery()) {
                    return rs.next() ? rs.getInt("card_id") : 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PaymentCardDAO.save failed: " + e.getMessage(), e);
        }
    }

    public boolean deleteForUser(int cardId, int userId) {
        final String sql = "DELETE FROM payment_cards WHERE card_id = ? AND user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, cardId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("PaymentCardDAO.deleteForUser failed: " + e.getMessage(), e);
        }
    }

    private PaymentCard mapRow(ResultSet rs) throws SQLException {
        PaymentCard c = new PaymentCard();
        c.setCardId(rs.getInt("card_id"));
        c.setUserId(rs.getInt("user_id"));
        c.setBrand(rs.getString("brand"));
        c.setLast4(rs.getString("last4"));
        c.setExpiry(rs.getString("expiry"));
        c.setEncryptedCardNumber(rs.getString("encrypted_card_number"));
        c.setCardIv(rs.getString("card_iv"));
        try {
            c.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        } catch (Exception ignored) {
            Timestamp ts = rs.getTimestamp("created_at");
            c.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        }
        return c;
    }
}
