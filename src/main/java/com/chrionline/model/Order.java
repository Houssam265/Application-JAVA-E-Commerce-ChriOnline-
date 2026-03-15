package com.chrionline.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the {@code orders} table.
 *
 * columns: order_id CHAR(36) UUID PK, user_id, status (OrderStatus),
 *          total_amount DECIMAL(10,2), created_at, updated_at
 *
 * Owns a list of {@link OrderItem}s for convenience — populated by the DAO,
 * not persisted directly (no DB access here).
 */
public class Order {

    // ── Fields ─────────────────────────────────────────────────────────────
    private String        orderId;       // CHAR(36) plain UUID
    private int           userId;
    private OrderStatus   status;        // enum — NOT a plain String
    private double        totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Convenience list populated by DAO — not a DB column. */
    private List<OrderItem> items = new ArrayList<>();

    // ── Constructors ────────────────────────────────────────────────────────

    public Order() {}

    public Order(String orderId, int userId, OrderStatus status,
                 double totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.orderId      = orderId;
        this.userId       = userId;
        this.status       = status;
        this.totalAmount  = totalAmount;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public String      getOrderId()          { return orderId; }
    public void        setOrderId(String v)  { this.orderId = v; }

    public int         getUserId()       { return userId; }
    public void        setUserId(int v)  { this.userId = v; }

    public OrderStatus getStatus()              { return status; }
    public void        setStatus(OrderStatus v) { this.status = v; }

    public double      getTotalAmount()          { return totalAmount; }
    public void        setTotalAmount(double v)  { this.totalAmount = v; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    public LocalDateTime getUpdatedAt()               { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }

    public List<OrderItem> getItems()                  { return items; }
    public void            setItems(List<OrderItem> v) { this.items = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Order{" +
               "orderId='"      + orderId             + '\'' +
               ", userId="      + userId              +
               ", status="      + status              +
               ", totalAmount=" + totalAmount         +
               ", createdAt="   + createdAt           +
               ", updatedAt="   + updatedAt           +
               ", items="       + items.size() + " item(s)" +
               '}';
    }
}
