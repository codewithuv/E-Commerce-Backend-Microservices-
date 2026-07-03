package com.ecommerce.inventory;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    private UUID productId;

    @Column(nullable = false, unique = true)
    private String sku;

    /** Physical stock on hand. */
    @Column(nullable = false)
    private int availableQuantity;

    /** Stock held by in-flight sagas — not yet shipped, not yet sellable. */
    @Column(nullable = false)
    private int reservedQuantity;

    @Version
    private long version;   // optimistic locking prevents overselling under concurrency

    protected InventoryItem() {}

    public boolean canReserve(int qty) {
        return availableQuantity - reservedQuantity >= qty;
    }

    public void reserve(int qty) {
        if (!canReserve(qty)) {
            throw new IllegalStateException("Insufficient stock for product " + productId);
        }
        reservedQuantity += qty;
    }

    /** Compensating action when the saga fails downstream. */
    public void release(int qty) {
        reservedQuantity = Math.max(0, reservedQuantity - qty);
    }

    /** Called on order completion: reservation converts to a real decrement. */
    public void commitReservation(int qty) {
        reservedQuantity -= qty;
        availableQuantity -= qty;
    }

    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
}
