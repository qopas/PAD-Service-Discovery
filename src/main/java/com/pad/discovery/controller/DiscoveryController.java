package com.pad.discovery.controller;

import com.pad.discovery.dto.ApiResponse;
import com.pad.discovery.dto.HeartbeatRequest;
import com.pad.discovery.dto.RegisterRequest;
import com.pad.discovery.model.ServiceInstance;
import com.pad.discovery.service.LogService;
import com.pad.discovery.service.RegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Service Discovery operations.
 * Provides endpoints for service registration, discovery, and health management.
 */
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Slf4j
public class DiscoveryController {
    
    private final RegistryService registryService;
    private final LogService logService;
    
    /**
     * Register a new service instance.
     * 
     * POST /api/discovery/register
     * 
     * @param request Registration request containing service details
     * @return Registered service instance
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<ServiceInstance>> registerService(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request: serviceName={}, instanceId={}", 
                request.getServiceName(), request.getInstanceId());
        
        ServiceInstance instance = ServiceInstance.builder()
                .serviceName(request.getServiceName())
                .serviceUrl(request.getServiceUrl())
                .instanceId(request.getInstanceId())
                .currentLoad(request.getCurrentLoad() != null ? request.getCurrentLoad() : 0.0)
                .requestCount(request.getRequestCount() != null ? request.getRequestCount() : 0)
                .build();
        
        ServiceInstance registered = registryService.register(instance);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(registered, "Service instance registered successfully"));
    }
    
    /**
     * Get all instances of a specific service.
     * 
     * GET /api/discovery/services/{serviceName}
     * 
     * @param serviceName Name of the service
     * @return List of service instances
     */
    @GetMapping("/services/{serviceName}")
    public ResponseEntity<ApiResponse<List<ServiceInstance>>> getServiceInstances(
            @PathVariable String serviceName) {
        
        log.debug("Received request to get instances for service: {}", serviceName);
        
        List<ServiceInstance> instances = registryService.getServiceInstances(serviceName);
        
        if (instances.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No instances found for service: " + serviceName));
        }
        
        return ResponseEntity.ok(ApiResponse.success(
                instances, 
                String.format("Found %d instance(s) for service: %s", instances.size(), serviceName)
        ));
    }
    
    /**
     * Get all instances of a service sorted by load (lowest to highest).
     * 
     * GET /api/discovery/services/{serviceName}/by-load
     * 
     * @param serviceName Name of the service
     * @return List of service instances sorted by load
     */
    @GetMapping("/services/{serviceName}/by-load")
    public ResponseEntity<ApiResponse<List<ServiceInstance>>> getServiceInstancesByLoad(
            @PathVariable String serviceName) {
        
        log.debug("Received request to get instances for service sorted by load: {}", serviceName);
        
        List<ServiceInstance> instances = registryService.getServiceInstancesByLoad(serviceName);
        
        if (instances.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No healthy instances found for service: " + serviceName));
        }
        
        return ResponseEntity.ok(ApiResponse.success(
                instances,
                String.format("Found %d healthy instance(s) for service: %s (sorted by load)", 
                        instances.size(), serviceName)
        ));
    }
    
    /**
     * Update heartbeat for a service instance.
     * 
     * POST /api/discovery/heartbeat
     * 
     * @param request Heartbeat request with instance ID and optional load information
     * @return Success or error response
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> updateHeartbeat(@Valid @RequestBody HeartbeatRequest request) {
        log.debug("Received heartbeat from instance: {}", request.getInstanceId());
        
        boolean updated = registryService.updateHeartbeat(
                request.getInstanceId(),
                request.getCurrentLoad(),
                request.getRequestCount()
        );
        
        if (updated) {
            return ResponseEntity.ok(ApiResponse.success(null, "Heartbeat updated successfully"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Service instance not found: " + request.getInstanceId()));
        }
    }
    
    /**
     * Deregister a service instance.
     * 
     * DELETE /api/discovery/deregister/{instanceId}
     * 
     * @param instanceId Unique identifier of the instance to deregister
     * @return Success or error response
     */
    @DeleteMapping("/deregister/{instanceId}")
    public ResponseEntity<ApiResponse<Void>> deregisterService(@PathVariable String instanceId) {
        log.info("Received deregistration request for instance: {}", instanceId);
        
        boolean removed = registryService.deregister(instanceId);
        
        if (removed) {
            return ResponseEntity.ok(ApiResponse.success(null, "Service instance deregistered successfully"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Service instance not found: " + instanceId));
        }
    }
    
    /**
     * Get all registered services with their instances.
     * 
     * GET /api/discovery/services
     * 
     * @return Map of service names to their instances
     */
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<Map<String, List<ServiceInstance>>>> getAllServices() {
        log.debug("Received request to get all services");
        
        Map<String, List<ServiceInstance>> allServices = registryService.getAllServices();
        
        int totalInstances = allServices.values().stream()
                .mapToInt(List::size)
                .sum();
        
        return ResponseEntity.ok(ApiResponse.success(
                allServices,
                String.format("Found %d service(s) with %d total instance(s)", 
                        allServices.size(), totalInstances)
        ));
    }
    
    /**
     * Download recent logs as a file.
     * 
     * GET /api/discovery/logs
     * 
     * @return Log file for download
     */
    @GetMapping("/logs")
    public ResponseEntity<Resource> downloadLogs() {
        log.info("Received request to download logs");
        
        try {
            Resource logFile = logService.getLogFile();
            String filename = logService.getLogFileName();
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(logFile);
                    
        } catch (IOException e) {
            log.error("Error retrieving log file: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
    
    /**
     * Get recent log entries as JSON.
     * 
     * GET /api/discovery/logs/recent
     * 
     * @param lines Number of recent lines to retrieve (default: 100)
     * @return Recent log entries
     */
    @GetMapping("/logs/recent")
    public ResponseEntity<ApiResponse<List<String>>> getRecentLogs(
            @RequestParam(defaultValue = "100") int lines) {
        
        log.debug("Received request to get {} recent log lines", lines);
        
        if (lines <= 0 || lines > 10000) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Lines parameter must be between 1 and 10000"));
        }
        
        List<String> logs = logService.getRecentLogs(lines);
        
        return ResponseEntity.ok(ApiResponse.success(
                logs,
                String.format("Retrieved %d log lines", logs.size())
        ));
    }
    
    /**
     * Health check endpoint for the service discovery itself.
     * 
     * GET /api/discovery/health
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "service", "service-discovery",
                "registeredServices", registryService.getAllServices().size(),
                "totalInstances", registryService.getAllInstances().size()
        );
        
        return ResponseEntity.ok(ApiResponse.success(health, "Service Discovery is healthy"));
    }
    
    /**
     * Status endpoint (required for health checks by other services).
     * 
     * GET /status
     * 
     * @return Simple status response
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "load", 0.0,
                "requestCount", 0,
                "timestamp", LocalDateTime.now()
        ));
    }
}

