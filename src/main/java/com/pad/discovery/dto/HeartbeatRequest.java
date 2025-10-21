package com.pad.discovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for heartbeat update request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    
    @NotBlank(message = "Instance ID is required")
    private String instanceId;
    
    private Double currentLoad;
    
    private Integer requestCount;
}

