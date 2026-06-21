package com.chrionline.model;

import java.time.LocalDateTime;

public class PaymentCard {
    private int cardId;
    private int userId;
    private String brand;
    private String last4;
    private String expiry;
    private String encryptedCardNumber;
    private String cardIv;
    private LocalDateTime createdAt;

    public int getCardId() { return cardId; }
    public void setCardId(int cardId) { this.cardId = cardId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getEncryptedCardNumber() { return encryptedCardNumber; }
    public void setEncryptedCardNumber(String encryptedCardNumber) { this.encryptedCardNumber = encryptedCardNumber; }
    public String getCardIv() { return cardIv; }
    public void setCardIv(String cardIv) { this.cardIv = cardIv; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
