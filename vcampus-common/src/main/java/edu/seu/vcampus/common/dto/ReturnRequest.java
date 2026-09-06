package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used to return one borrow record. */
public final class ReturnRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int recordId;

    public ReturnRequest(int recordId) {
        this.recordId = recordId;
    }

    public int getRecordId() {
        return recordId;
    }
}
