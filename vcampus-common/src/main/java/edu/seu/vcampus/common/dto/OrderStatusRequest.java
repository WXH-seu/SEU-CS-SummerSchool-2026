package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Admin updates the status of one order. */
public final class OrderStatusRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final String statusName;

    public OrderStatusRequest(String orderId, String statusName) {
        this.orderId = orderId;
        this.statusName = statusName;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatusName() {
        return statusName;
    }
}
