package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Identifies one academic record for a delete operation. */
public final class EntityIdRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String entityId;

    public EntityIdRequest(String entityId) {
        this.entityId = entityId;
    }

    public String getEntityId() {
        return entityId;
    }
}
