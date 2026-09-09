package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used by administrators to approve or reject a reservation. */
public final class ReserveReviewRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int reservationId;
    private final boolean approved;

    public ReserveReviewRequest(int reservationId, boolean approved) {
        this.reservationId = reservationId;
        this.approved = approved;
    }

    public int getReservationId() {
        return reservationId;
    }

    public boolean isApproved() {
        return approved;
    }
}
