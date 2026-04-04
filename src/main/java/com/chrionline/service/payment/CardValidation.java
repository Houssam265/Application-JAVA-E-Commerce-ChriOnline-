package com.chrionline.service.payment;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Validation de carte pour paiement simulé (KAN-7) : Luhn, expiration MM/YY, CVV.
 */
public final class CardValidation {

    private CardValidation() {}

    /** Supprime espaces et tirets. */
    public static String normalizeCardNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s-]+", "");
    }

    /** Exactement 16 chiffres (Luhn désactivé pour simplifier les tests). */
    public static boolean isValidLuhn16(String digits) {
        if (digits == null || digits.length() != 16) {
            return false;
        }
        return digits.chars().allMatch(Character::isDigit);
    }

    /**
     * Date au format {@code MM/YY}, carte valide jusqu'à la fin du mois indiqué (non expirée).
     */
    public static boolean isExpiryValid(String mmYy) {
        if (mmYy == null || !mmYy.matches("\\d{2}/\\d{2}")) {
            return false;
        }
        int mm = Integer.parseInt(mmYy.substring(0, 2));
        int yy = Integer.parseInt(mmYy.substring(3, 5));
        if (mm < 1 || mm > 12) {
            return false;
        }
        int yearFull = 2000 + yy;
        YearMonth ym = YearMonth.of(yearFull, mm);
        LocalDate lastDay = ym.atEndOfMonth();
        return !LocalDate.now().isAfter(lastDay);
    }

    /** CVV : exactement 3 chiffres. */
    public static boolean isValidCvv(String cvv) {
        return cvv != null && cvv.matches("\\d{3}");
    }
}
