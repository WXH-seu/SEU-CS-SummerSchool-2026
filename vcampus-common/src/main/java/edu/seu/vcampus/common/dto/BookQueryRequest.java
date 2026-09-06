package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filter used by library book queries. */
public final class BookQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final boolean includeInactive;

    public BookQueryRequest(String keyword) {
        this(keyword, false);
    }

    public BookQueryRequest(String keyword, boolean includeInactive) {
        this.keyword = keyword;
        this.includeInactive = includeInactive;
    }

    public String getKeyword() {
        return keyword;
    }

    public boolean isIncludeInactive() {
        return includeInactive;
    }
}
