package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** One line of a user's shopping cart, joined with product information. */
public final class CartItemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final int stock;
    private final boolean active;

    public CartItemDto(String productId, String productName, BigDecimal unitPrice,
                       int quantity, int stock, boolean active) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.stock = stock;
        this.active = active;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }

    /** Calculated line total using fixed-point arithmetic. */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
