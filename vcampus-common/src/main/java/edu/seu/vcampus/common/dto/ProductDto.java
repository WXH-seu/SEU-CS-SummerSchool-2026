package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** Product data shared by client and server. */
public final class ProductDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String productId;
    private final String productName;
    private final String category;
    private final String description;
    private final BigDecimal price;
    private final int stock;
    private final boolean active;

    public ProductDto(String productId, String productName, String category,
                      String description, BigDecimal price, int stock, boolean active) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.active = active;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }
}
