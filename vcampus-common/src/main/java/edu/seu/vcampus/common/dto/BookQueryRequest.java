package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filter used by library book queries. */
public final class BookQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;

    public BookQueryRequest(String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword() {
        return keyword;
    }
}
