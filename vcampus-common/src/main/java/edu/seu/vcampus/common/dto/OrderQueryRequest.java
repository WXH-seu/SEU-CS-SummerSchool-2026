package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/**
 * 订单筛选条件：状态、商品分类与时间范围。
 * 字段为空表示不限制；timeRange 取值：全部 / 今天 / 近7天 / 近30天。
 */
public final class OrderQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String statusName;
    private final String category;
    private final String timeRange;

    public OrderQueryRequest(String statusName, String category, String timeRange) {
        this.statusName = statusName;
        this.category = category;
        this.timeRange = timeRange;
    }

    public String getStatusName() {
        return statusName;
    }

    public String getCategory() {
        return category;
    }

    public String getTimeRange() {
        return timeRange;
    }
}
