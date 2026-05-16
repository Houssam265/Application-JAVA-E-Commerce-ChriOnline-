package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.PaymentCardDAO;
import com.chrionline.dao.PaymentDAO;
import com.chrionline.model.Order;
import com.chrionline.model.Payment;
import com.chrionline.model.PaymentCard;
import com.chrionline.security.AESUtil;
import com.chrionline.security.PaymentCardCrypto;
import com.chrionline.service.payment.CardValidation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PaymentService {

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final PaymentCardDAO paymentCardDAO = new PaymentCardDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    private static final double SIMULATION_SUCCESS_RATE = 0.90;

    public Map<String, Object> processSimulatedCardPayment(int userId,
                                                           int orderId,
                                                           String cardNumberRaw,
                                                           String expiryMmYy,
                                                           String cvv) {
        return processSimulatedCardPayment(userId, orderId, cardNumberRaw, expiryMmYy, cvv, false);
    }

    public Map<String, Object> processSimulatedCardPayment(int userId,
                                                           int orderId,
                                                           String cardNumberRaw,
                                                           String expiryMmYy,
                                                           String cvv,
                                                           boolean saveCard) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("order_id requis.");
        }

        Order order = orderDAO.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable."));
        if (order.getUserId() != userId) {
            throw new IllegalArgumentException("Cette commande ne vous appartient pas.");
        }

        Optional<Payment> existing = paymentDAO.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().getStatus() == Payment.Status.SUCCESS) {
            throw new IllegalArgumentException("Cette commande est déjà payée.");
        }

        double amount = order.getTotalAmount();
        LocalDateTime now = LocalDateTime.now();

        String digits = CardValidation.normalizeCardNumber(cardNumberRaw);
        String expiry = expiryMmYy != null ? expiryMmYy.trim() : "";

        boolean luhnOk = CardValidation.isValidLuhn16(digits);
        boolean expiryOk = CardValidation.isExpiryValid(expiry);
        boolean cvvOk = CardValidation.isValidCvv(cvv);

        if (!luhnOk || !expiryOk || !cvvOk) {
            String reason = buildValidationErrorMessage(luhnOk, expiryOk, cvvOk);
            paymentDAO.upsertPayment(orderId, Payment.Method.CREDIT_CARD, Payment.Status.FAILED, amount, null, now);
            Payment saved = paymentDAO.findByOrderId(orderId).orElseThrow();
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("status", "FAILED");
            err.put("reason", "VALIDATION");
            err.put("message", reason);
            err.put("paymentId", saved.getPaymentId());
            err.put("orderId", orderId);
            err.put("paidAt", now.toString());
            return err;
        }

        boolean accepted = ThreadLocalRandom.current().nextDouble() < SIMULATION_SUCCESS_RATE;
        String txId = "TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        if (!accepted) {
            String failTx = "SIM_REFUSED_" + UUID.randomUUID().toString().substring(0, 8);
            paymentDAO.upsertPayment(orderId, Payment.Method.CREDIT_CARD, Payment.Status.FAILED, amount, failTx, now);
            Payment saved = paymentDAO.findByOrderId(orderId).orElseThrow();
            Map<String, Object> res = new HashMap<>();
            res.put("success", false);
            res.put("status", "FAILED");
            res.put("reason", "SIMULATION");
            res.put("message", "Paiement refusé par le simulateur (aléatoire).");
            res.put("paymentId", saved.getPaymentId());
            res.put("orderId", orderId);
            res.put("transactionId", failTx);
            res.put("paidAt", now.toString());
            return res;
        }

        try {
            orderDAO.completePaymentWithStockDecrement(orderId, Payment.Method.CREDIT_CARD, amount, txId, now);
        } catch (IllegalArgumentException e) {
            Map<String, Object> stockErr = new HashMap<>();
            stockErr.put("success", false);
            stockErr.put("status", "FAILED");
            stockErr.put("reason", "STOCK");
            stockErr.put("message", e.getMessage());
            stockErr.put("orderId", orderId);
            stockErr.put("paidAt", now.toString());
            return stockErr;
        }

        Payment saved = paymentDAO.findByOrderId(orderId).orElseThrow();
        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        ok.put("status", "SUCCESS");
        ok.put("paymentId", saved.getPaymentId());
        ok.put("orderId", orderId);
        ok.put("transactionId", txId);
        ok.put("amount", amount);
        ok.put("paidAt", now.toString());
        if (saveCard) {
            int cardId = saveCardForUser(userId, digits, expiry);
            ok.put("savedCardId", cardId);
            ok.put("savedCardLast4", PaymentCardCrypto.last4(digits));
        }
        return ok;
    }

    public Map<String, Object> processPaymentWithSavedCard(int userId,
                                                           int orderId,
                                                           int cardId,
                                                           String cvv) {
        PaymentCard card = paymentCardDAO.findByIdForUser(cardId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Carte enregistree introuvable."));
        String cardNumber = PaymentCardCrypto.decryptCardNumber(card.getEncryptedCardNumber(), card.getCardIv());
        return processSimulatedCardPayment(userId, orderId, cardNumber, card.getExpiry(), cvv, false);
    }

    public List<Map<String, Object>> listSavedCards(int userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentCard card : paymentCardDAO.findByUserId(userId)) {
            Map<String, Object> row = new HashMap<>();
            row.put("cardId", card.getCardId());
            row.put("brand", card.getBrand());
            row.put("last4", card.getLast4());
            row.put("expiry", card.getExpiry());
            row.put("label", card.getBrand() + " **** " + card.getLast4() + " - " + card.getExpiry());
            out.add(row);
        }
        return out;
    }

    public boolean deleteSavedCard(int userId, int cardId) {
        return paymentCardDAO.deleteForUser(cardId, userId);
    }

    private int saveCardForUser(int userId, String digits, String expiry) {
        AESUtil.Sealed sealed = PaymentCardCrypto.encryptCardNumber(digits);
        return paymentCardDAO.save(
                userId,
                PaymentCardCrypto.detectBrand(digits),
                PaymentCardCrypto.last4(digits),
                PaymentCardCrypto.normalizedExpiry(expiry),
                sealed.cipherTextBase64(),
                sealed.ivBase64());
    }

    private static String buildValidationErrorMessage(boolean luhnOk, boolean expiryOk, boolean cvvOk) {
        StringBuilder sb = new StringBuilder("Données carte invalides : ");
        if (!luhnOk) sb.append("numéro (16 chiffres, Luhn). ");
        if (!expiryOk) sb.append("date d'expiration (MM/YY, non expirée). ");
        if (!cvvOk) sb.append("CVV (3 chiffres). ");
        return sb.toString().trim();
    }
}
