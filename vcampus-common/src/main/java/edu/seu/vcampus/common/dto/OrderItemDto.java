package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** A single purchased line inside an order. */
public final class OrderItemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orderItemId;
    private final String orderId;
    private final String productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal subtotal;

    public OrderItemDto(String orderItemId, String orderId, String productId,
                        String productName, BigDecimal unitPrice, int quantity,
                        BigDecimal subtotal) {
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public String getOrderId() {
        return orderId;
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

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
