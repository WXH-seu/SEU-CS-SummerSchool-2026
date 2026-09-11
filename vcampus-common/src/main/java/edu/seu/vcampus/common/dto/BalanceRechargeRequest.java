package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** 校园钱包充值请求：金额 + 支付渠道。 */
public final class BalanceRechargeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final BigDecimal amount;
    private final String channel;

    public BalanceRechargeRequest(BigDecimal amount, String channel) {
        this.amount = amount;
        this.channel = channel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getChannel() {
        return channel;
    }
}
