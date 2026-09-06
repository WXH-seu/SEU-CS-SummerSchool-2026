package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filter used by product queries. */
public final class StoreQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final String category;
    private final boolean activeOnly;

    public StoreQueryRequest(String keyword, String category, boolean activeOnly) {
        this.keyword = keyword;
        this.category = category;
        this.activeOnly = activeOnly;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getCategory() {
        return category;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }
}
