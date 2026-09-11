package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Adds or updates one product inside a shopping cart. */
public final class CartUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String productId;
    private final int quantity;

    public CartUpdateRequest(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
