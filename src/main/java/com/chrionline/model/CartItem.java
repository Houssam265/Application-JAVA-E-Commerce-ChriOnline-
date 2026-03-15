package com.chrionline.model;

/**
 * Maps to the {@code cart_items} table.
 *
 * columns: cart_item_id (PK), cart_id (FK), product_id (FK),
 *          quantity INT (>0), unit_price DECIMAL(10,2) — snapshot at add time.
 *
 * UNIQUE constraint in schema: (cart_id, product_id) — enforced at DB level.
 */
public class CartItem {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int    cartItemId;
    private int    cartId;
    private int    productId;
    private int    quantity;
    private double unitPrice;   // price snapshot at add time

    // ── Constructors ────────────────────────────────────────────────────────

    public CartItem() {}

    public CartItem(int cartItemId, int cartId, int productId,
                    int quantity, double unitPrice) {
        this.cartItemId = cartItemId;
        this.cartId     = cartId;
        this.productId  = productId;
        this.quantity   = quantity;
        this.unitPrice  = unitPrice;
    }

    // ── Derived calculation ─────────────────────────────────────────────────

    /**
     * Returns {@code unitPrice × quantity}.
     * Uses the same formula as {@link OrderItem#getSubtotal()} so that
     * cart totals and order totals are always computed identically.
     */
    public double getSubtotal() {
        return unitPrice * quantity;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int    getCartItemId()          { return cartItemId; }
    public void   setCartItemId(int v)     { this.cartItemId = v; }

    public int    getCartId()          { return cartId; }
    public void   setCartId(int v)     { this.cartId = v; }

    public int    getProductId()          { return productId; }
    public void   setProductId(int v)     { this.productId = v; }

    public int    getQuantity()        { return quantity; }
    public void   setQuantity(int v)   { this.quantity = v; }

    public double getUnitPrice()          { return unitPrice; }
    public void   setUnitPrice(double v)  { this.unitPrice = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "CartItem{" +
               "cartItemId=" + cartItemId +
               ", cartId="   + cartId     +
               ", productId="+ productId  +
               ", quantity=" + quantity   +
               ", unitPrice="+ unitPrice  +
               ", subtotal=" + getSubtotal() +
               '}';
    }
}
