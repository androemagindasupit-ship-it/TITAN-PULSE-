package com.titanpulse.app;

import com.titanpulse.app.core.JobStatus;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class JobStatusTest {
    @Test public void statusesAreStable(){
        assertEquals("QUEUED", JobStatus.QUEUED);
        assertEquals("RUNNING", JobStatus.RUNNING);
        assertEquals("COMPLETED", JobStatus.COMPLETED);
        assertEquals("FAILED", JobStatus.FAILED);
        assertEquals("RETRYING", JobStatus.RETRYING);
        assertEquals("CANCELLED", JobStatus.CANCELLED);
        assertEquals("WAITING_NETWORK", JobStatus.WAITING_NETWORK);
    }
}
