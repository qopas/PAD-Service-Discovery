package com.pad.discovery.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the circuit breaker state for a service instance.
 * Tracks failures and determines when to trip the circuit breaker.
 */
@Data
public class CircuitBreakerState {
    
    private String instanceId;
    private List<LocalDateTime> failureTimestamps;
    private boolean circuitOpen;
    
    public CircuitBreakerState(String instanceId) {
        this.instanceId = instanceId;
        this.failureTimestamps = new ArrayList<>();
        this.circuitOpen = false;
    }
    
    /**
     * Record a failure for this instance
     */
    public void recordFailure() {
        failureTimestamps.add(LocalDateTime.now());
    }
    
    /**
     * Remove failures outside the time window
     */
    public void removeOldFailures(LocalDateTime cutoffTime) {
        failureTimestamps.removeIf(timestamp -> timestamp.isBefore(cutoffTime));
    }
    
    /**
     * Get the count of recent failures
     */
    public int getRecentFailureCount() {
        return failureTimestamps.size();
    }
    
    /**
     * Clear all failures (e.g., when service becomes healthy again)
     */
    public void reset() {
        failureTimestamps.clear();
        circuitOpen = false;
    }
}

