package com.pad.discovery.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a registered service instance in the service registry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstance {
    
    /**
     * Name of the service (e.g., "user-service", "payment-service")
     */
    private String serviceName;
    
    /**
     * Base URL of the service instance (e.g., "http://localhost:8080")
     */
    private String serviceUrl;
    
    /**
     * Unique identifier for this service instance
     */
    private String instanceId;
    
    /**
     * Timestamp of the last heartbeat received from this instance
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastHeartbeat;
    
    /**
     * Current health status of the service instance
     */
    private ServiceStatus status;
    
    /**
     * Current load on the service (CPU/memory usage percentage, 0-100)
     */
    private Double currentLoad;
    
    /**
     * Number of requests currently being processed
     */
    private Integer requestCount;

    private HeartbeatMode heartbeatMode = HeartbeatMode.OPTIONAL;
    
    /**
     * Enum representing the health status of a service instance
     */
    public enum ServiceStatus {
        HEALTHY,
        UNHEALTHY
    }
}

