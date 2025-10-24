package com.pad.discovery.dto;

import com.pad.discovery.model.HeartbeatMode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for service registration request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    @NotBlank(message = "Service name is required")
    private String serviceName;
    
    @NotBlank(message = "Service URL is required")
    private String serviceUrl;
    
    @NotBlank(message = "Instance ID is required")
    private String instanceId;
    
    private Double currentLoad;
    
    private Integer requestCount;
    private HeartbeatMode heartbeatMode;
}

