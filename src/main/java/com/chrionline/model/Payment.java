package com.chrionline.model;

import java.time.LocalDateTime;

/**
 * Maps to the {@code payments} table.
 *
 * columns: payment_id (PK), order_id CHAR(36) UNIQUE FK,
 *          method ENUM('CREDIT_CARD','SIMULATED'),
 *          status ENUM('PENDING','SUCCESS','FAILED'),
 *          amount DECIMAL(10,2),
 *          transaction_id VARCHAR(100) UNIQUE nullable,
 *          paid_at DATETIME nullable
 */
public class Payment {

    // ── Nested enums matching schema ENUMs exactly ─────────────────────────

    /** Schema: ENUM('CREDIT_CARD','SIMULATED') */
    public enum Method {
        CREDIT_CARD,
        SIMULATED
    }

    /** Schema: ENUM('PENDING','SUCCESS','FAILED') */
    public enum Status {
        PENDING,
        SUCCESS,
        FAILED
    }

    // ── Fields ─────────────────────────────────────────────────────────────
    private int           paymentId;
    private String        orderId;          // CHAR(36) FK → orders.order_id
    private Method        method;
    private Status        status;
    private double        amount;
    private String        transactionId;   // nullable VARCHAR(100)
    private LocalDateTime paidAt;          // nullable DATETIME

    // ── Constructors ────────────────────────────────────────────────────────

    public Payment() {}

    public Payment(int paymentId, String orderId, Method method, Status status,
                   double amount, String transactionId, LocalDateTime paidAt) {
        this.paymentId     = paymentId;
        this.orderId       = orderId;
        this.method        = method;
        this.status        = status;
        this.amount        = amount;
        this.transactionId = transactionId;
        this.paidAt        = paidAt;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int           getPaymentId()          { return paymentId; }
    public void          setPaymentId(int v)     { this.paymentId = v; }

    public String        getOrderId()          { return orderId; }
    public void          setOrderId(String v)  { this.orderId = v; }

    public Method        getMethod()           { return method; }
    public void          setMethod(Method v)   { this.method = v; }

    public Status        getStatus()           { return status; }
    public void          setStatus(Status v)   { this.status = v; }

    public double        getAmount()          { return amount; }
    public void          setAmount(double v)  { this.amount = v; }

    public String        getTransactionId()          { return transactionId; }
    public void          setTransactionId(String v)  { this.transactionId = v; }

    public LocalDateTime getPaidAt()               { return paidAt; }
    public void          setPaidAt(LocalDateTime v){ this.paidAt = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Payment{" +
               "paymentId="       + paymentId     +
               ", orderId='"      + orderId       + '\'' +
               ", method="        + method        +
               ", status="        + status        +
               ", amount="        + amount        +
               ", transactionId='"+ transactionId + '\'' +
               ", paidAt="        + paidAt        +
               '}';
    }
}
