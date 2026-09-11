package com.titanpulse.app.core;

public final class JobStatus {
    public static final String QUEUED="QUEUED", RUNNING="RUNNING", COMPLETED="COMPLETED", FAILED="FAILED", RETRYING="RETRYING", CANCELLED="CANCELLED", WAITING_NETWORK="WAITING_NETWORK";
    private JobStatus() {}
}
