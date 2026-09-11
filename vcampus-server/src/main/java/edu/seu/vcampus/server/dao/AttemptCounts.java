package edu.seu.vcampus.server.dao;

/** Enrolled counts of one section split by attempt type. */
public final class AttemptCounts {
    private final int firstAttemptCount;
    private final int retakeCount;

    public AttemptCounts(int firstAttemptCount, int retakeCount) {
        this.firstAttemptCount = firstAttemptCount;
        this.retakeCount = retakeCount;
    }

    public int getFirstAttemptCount() {
        return firstAttemptCount;
    }

    public int getRetakeCount() {
        return retakeCount;
    }

    public int getTotal() {
        return firstAttemptCount + retakeCount;
    }
}
