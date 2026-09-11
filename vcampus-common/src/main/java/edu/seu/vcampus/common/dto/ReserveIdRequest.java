package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Identifies one reservation for pickup. */
public final class ReserveIdRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int reservationId;

    public ReserveIdRequest(int reservationId) {
        this.reservationId = reservationId;
    }

    public int getReservationId() {
        return reservationId;
    }
}
