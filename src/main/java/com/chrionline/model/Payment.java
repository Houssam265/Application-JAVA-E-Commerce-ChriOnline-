package com.chrionline.model;

import java.time.LocalDateTime;

public class Payment {

    public enum Method {
        CREDIT_CARD,
        SIMULATED
    }

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED
    }

    private int paymentId;
    private int orderId;
    private Method method;
    private Status status;
    private double amount;
    private String transactionId;
    private LocalDateTime paidAt;

    public Payment() {
    }

    public Payment(int paymentId, int orderId, Method method, Status status,
                   double amount, String transactionId, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.method = method;
        this.status = status;
        this.amount = amount;
        this.transactionId = transactionId;
        this.paidAt = paidAt;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId=" + paymentId +
                ", orderId=" + orderId +
                ", method=" + method +
                ", status=" + status +
                ", amount=" + amount +
                ", transactionId='" + transactionId + '\'' +
                ", paidAt=" + paidAt +
                '}';
    }
}
