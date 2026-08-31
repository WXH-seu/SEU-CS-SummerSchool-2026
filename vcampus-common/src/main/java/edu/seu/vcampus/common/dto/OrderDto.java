package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** An order header together with its purchased lines. */
public final class OrderDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final String userId;
    private final BigDecimal totalAmount;
    private final String statusName;
    private final String orderTime;
    private final List<OrderItemDto> items;

    public OrderDto(String orderId, String userId, BigDecimal totalAmount,
                    String statusName, String orderTime, List<OrderItemDto> items) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.statusName = statusName;
        this.orderTime = orderTime;
        this.items = new ArrayList<OrderItemDto>(items);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getStatusName() {
        return statusName;
    }

    public String getOrderTime() {
        return orderTime;
    }

    public List<OrderItemDto> getItems() {
        return new ArrayList<OrderItemDto>(items);
    }
}
