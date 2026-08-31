package edu.seu.vcampus.client.ui;

import java.math.BigDecimal;

/** Shared display formatting for the store pages. */
final class StoreFormat {
    private StoreFormat() {
    }

    static String money(BigDecimal value) {
        return String.format("%.2f", value == null ? BigDecimal.ZERO : value);
    }
}
