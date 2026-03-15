package com.chrionline.model;

/**
 * Matches the {@code status} column ENUM in the {@code orders} table exactly.
 *
 * Schema: ENUM('PENDING','VALIDATED','SHIPPED','DELIVERED','CANCELLED')
 *
 * Used by {@link Order#getStatus()} — never stored as a plain String.
 */
public enum OrderStatus {
    PENDING,
    VALIDATED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
