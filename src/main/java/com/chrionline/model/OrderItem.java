package com.chrionline.model;

/**
 * Maps to the {@code order_items} table.
 *
 * columns: order_item_id (PK), order_id CHAR(36), product_id,
 *          quantity INT, unit_price DECIMAL(10,2) — price snapshot at order time
 */
public class OrderItem {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int    orderItemId;
    private String orderId;       // CHAR(36) FK → orders.order_id
    private int    productId;
    private int    quantity;
    private double unitPrice;     // price snapshot at order time

    // ── Constructors ────────────────────────────────────────────────────────

    public OrderItem() {}

    public OrderItem(int orderItemId, String orderId, int productId,
                     int quantity, double unitPrice) {
        this.orderItemId = orderItemId;
        this.orderId     = orderId;
        this.productId   = productId;
        this.quantity    = quantity;
        this.unitPrice   = unitPrice;
    }

    // ── Derived calculation ─────────────────────────────────────────────────

    /**
     * Returns {@code unitPrice × quantity}.
     * Uses the same formula as {@link CartItem#getSubtotal()}.
     */
    public double getSubtotal() {
        return unitPrice * quantity;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int    getOrderItemId()          { return orderItemId; }
    public void   setOrderItemId(int v)     { this.orderItemId = v; }

    public String getOrderId()          { return orderId; }
    public void   setOrderId(String v)  { this.orderId = v; }

    public int    getProductId()          { return productId; }
    public void   setProductId(int v)     { this.productId = v; }

    public int    getQuantity()        { return quantity; }
    public void   setQuantity(int v)   { this.quantity = v; }

    public double getUnitPrice()          { return unitPrice; }
    public void   setUnitPrice(double v)  { this.unitPrice = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "OrderItem{" +
               "orderItemId=" + orderItemId +
               ", orderId='"  + orderId     + '\'' +
               ", productId=" + productId   +
               ", quantity="  + quantity    +
               ", unitPrice=" + unitPrice   +
               ", subtotal="  + getSubtotal() +
               '}';
    }
}
