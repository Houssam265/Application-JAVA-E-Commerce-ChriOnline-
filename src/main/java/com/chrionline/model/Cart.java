package com.chrionline.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the {@code carts} table.
 *
 * columns: cart_id (PK), user_id (FK UNIQUE — one cart per user),
 *          created_at, updated_at
 *
 * Owns a list of {@link CartItem}s for convenience — populated by the DAO,
 * not persisted as a column.
 */
public class Cart {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int           cartId;
    private int           userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Convenience list populated by DAO — not a DB column. */
    private List<CartItem> items = new ArrayList<>();

    // ── Constructors ────────────────────────────────────────────────────────

    public Cart() {}

    public Cart(int cartId, int userId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.cartId    = cartId;
        this.userId    = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Derived calculation ─────────────────────────────────────────────────

    /**
     * Sums {@link CartItem#getSubtotal()} across all items in the cart.
     * Consistent with {@link CartItem#getSubtotal()} (unitPrice × quantity)
     * so the total passed to OrderService when converting a cart to an order
     * is always computed the same way.
     *
     * @return total cart value as double, or 0.0 if the cart is empty
     */
    public double calculateTotal() {
        return items.stream()
                    .mapToDouble(CartItem::getSubtotal)
                    .sum();
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int           getCartId()       { return cartId; }
    public void          setCartId(int v)  { this.cartId = v; }

    public int           getUserId()       { return userId; }
    public void          setUserId(int v)  { this.userId = v; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    public LocalDateTime getUpdatedAt()               { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }

    public List<CartItem> getItems()                   { return items; }
    public void           setItems(List<CartItem> v)   { this.items = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Cart{" +
               "cartId="      + cartId           +
               ", userId="    + userId           +
               ", items="     + items.size()     + " item(s)" +
               ", total="     + calculateTotal() +
               ", updatedAt=" + updatedAt        +
               '}';
    }
}
