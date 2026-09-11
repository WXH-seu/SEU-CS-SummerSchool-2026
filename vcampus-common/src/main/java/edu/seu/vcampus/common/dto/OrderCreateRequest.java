package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 下单请求：携带购物车中本次要结算的商品编号。
 * 服务端只对勾选商品生成订单、扣库存并清空对应购物车行。
 */
public final class OrderCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> productIds;

    public OrderCreateRequest(List<String> productIds) {
        this.productIds = productIds == null
                ? new ArrayList<String>() : new ArrayList<String>(productIds);
    }

    public List<String> getProductIds() {
        return new ArrayList<String>(productIds);
    }
}
