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

    /** Exactement 16 chiffres et algorithme de Luhn. */
    public static boolean isValidLuhn16(String digits) {
        if (digits == null || digits.length() != 16) {
            return false;
        }
        if (!digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
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
