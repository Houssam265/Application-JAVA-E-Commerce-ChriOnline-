package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.PaymentDAO;
import com.chrionline.model.Order;
import com.chrionline.model.Payment;
import com.chrionline.service.payment.CardValidation;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KAN-7 — Paiement simulé : validation carte (Luhn, expiration, CVV), tirage 90 % / 10 %,
 * enregistrement en {@code payments} avec statut et horodatage.
 */
public class PaymentService {

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    private static final double SIMULATION_SUCCESS_RATE = 0.90;

    /**
     * Traite un paiement carte pour une commande appartenant à l'utilisateur.
     *
     * @param userId        utilisateur authentifié
     * @param orderId       UUID commande
     * @param cardNumberRaw numéro (espaces autorisés)
     * @param expiryMmYy    format MM/YY
     * @param cvv           3 chiffres
     * @return map décrivant le résultat (payload JSON côté TCP)
     */
    public Map<String, Object> processSimulatedCardPayment(int userId,
                                                           String orderId,
                                                           String cardNumberRaw,
                                                           String expiryMmYy,
                                                           String cvv) {
        if (orderId == null || orderId.isBlank()) {
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
            paymentDAO.upsertPayment(
                    orderId,
                    Payment.Method.CREDIT_CARD,
                    Payment.Status.FAILED,
                    amount,
                    null,
                    now
            );
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
            paymentDAO.upsertPayment(
                    orderId,
                    Payment.Method.CREDIT_CARD,
                    Payment.Status.FAILED,
                    amount,
                    failTx,
                    now
            );
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
            orderDAO.completePaymentWithStockDecrement(
                    orderId,
                    Payment.Method.CREDIT_CARD,
                    amount,
                    txId,
                    now);
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
        return ok;
    }

    private static String buildValidationErrorMessage(boolean luhnOk, boolean expiryOk, boolean cvvOk) {
        StringBuilder sb = new StringBuilder("Données carte invalides : ");
        if (!luhnOk) sb.append("numéro (16 chiffres, Luhn). ");
        if (!expiryOk) sb.append("date d'expiration (MM/YY, non expirée). ");
        if (!cvvOk) sb.append("CVV (3 chiffres). ");
        return sb.toString().trim();
    }
}
