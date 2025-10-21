package com.pad.discovery.service;

import com.pad.discovery.model.CircuitBreakerState;
import com.pad.discovery.model.ServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for monitoring health of registered service instances.
 * Implements circuit breaker pattern and automatic service removal.
 */
@Service
@Slf4j
public class HealthCheckService {
    
    private final RegistryService registryService;
    private final NotificationService notificationService;
    private final WebClient webClient;
    
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> highLoadAlertSent = new ConcurrentHashMap<>();
    
    @Value("${discovery.health-check.timeout}")
    private int healthCheckTimeout;
    
    @Value("${discovery.health-check.heartbeat-timeout}")
    private long heartbeatTimeout;
    
    @Value("${discovery.circuit-breaker.failure-threshold}")
    private int failureThreshold;
    
    @Value("${discovery.circuit-breaker.window-multiplier}")
    private double windowMultiplier;
    
    @Value("${discovery.load-threshold}")
    private double loadThreshold;
    
    public HealthCheckService(
            RegistryService registryService,
            NotificationService notificationService,
            WebClient.Builder webClientBuilder) {
        this.registryService = registryService;
        this.notificationService = notificationService;
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * Scheduled health check that runs every 30 seconds.
     * Checks all registered services and updates their health status.
     */
    @Scheduled(fixedRateString = "${discovery.health-check.interval}")
    public void performHealthCheck() {
        log.info("Starting scheduled health check");
        
        List<ServiceInstance> allInstances = registryService.getAllInstances();
        log.info("Checking health of {} service instances", allInstances.size());
        
        for (ServiceInstance instance : allInstances) {
            checkInstanceHealth(instance);
        }
        
        // Remove services with expired heartbeats
        removeExpiredHeartbeats();
        
        log.info("Completed scheduled health check");
    }
    
    /**
     * Check the health of a single service instance
     */
    private void checkInstanceHealth(ServiceInstance instance) {
        String statusUrl = instance.getServiceUrl() + "/status";
        String instanceId = instance.getInstanceId();
        
        log.debug("Checking health for instance: {} at {}", instanceId, statusUrl);
        
        webClient.get()
                .uri(statusUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(healthCheckTimeout))
                .subscribe(
                        response -> handleHealthCheckSuccess(instance, response),
                        error -> handleHealthCheckFailure(instance, error)
                );
    }
    
    /**
     * Handle successful health check response
     */
    private void handleHealthCheckSuccess(ServiceInstance instance, Map<String, Object> response) {
        log.debug("Health check successful for instance: {}", instance.getInstanceId());
        
        // Update service load if available
        if (response.containsKey("load")) {
            Double load = ((Number) response.get("load")).doubleValue();
            instance.setCurrentLoad(load);
            
            // Check for high load
            if (load > loadThreshold) {
                sendHighLoadAlertOnce(instance);
            } else {
                // Reset high load alert flag if load is back to normal
                highLoadAlertSent.remove(instance.getInstanceId());
            }
        }
        
        if (response.containsKey("requestCount")) {
            Integer requestCount = ((Number) response.get("requestCount")).intValue();
            instance.setRequestCount(requestCount);
        }
        
        // Mark as healthy and reset circuit breaker
        if (instance.getStatus() == ServiceInstance.ServiceStatus.UNHEALTHY) {
            registryService.updateInstanceStatus(instance.getInstanceId(), ServiceInstance.ServiceStatus.HEALTHY);
            log.info("Service instance recovered: instanceId={}, serviceName={}", 
                    instance.getInstanceId(), instance.getServiceName());
        }
        
        // Reset circuit breaker on successful health check
        CircuitBreakerState circuitBreaker = circuitBreakers.get(instance.getInstanceId());
        if (circuitBreaker != null) {
            circuitBreaker.reset();
        }
    }
    
    /**
     * Handle failed health check
     */
    private void handleHealthCheckFailure(ServiceInstance instance, Throwable error) {
        log.warn("Health check failed for instance: {}, error: {}", 
                instance.getInstanceId(), error.getMessage());
        
        // Record failure in circuit breaker
        CircuitBreakerState circuitBreaker = circuitBreakers.computeIfAbsent(
                instance.getInstanceId(), 
                id -> new CircuitBreakerState(id)
        );
        
        circuitBreaker.recordFailure();
        
        // Remove failures outside the time window
        long windowMillis = (long) (healthCheckTimeout * windowMultiplier);
        LocalDateTime cutoffTime = LocalDateTime.now().minusNanos(windowMillis * 1_000_000);
        circuitBreaker.removeOldFailures(cutoffTime);
        
        int recentFailures = circuitBreaker.getRecentFailureCount();
        log.info("Circuit breaker for instance {}: {} failures in window", 
                instance.getInstanceId(), recentFailures);
        
        // Check if failure threshold is reached
        if (recentFailures >= failureThreshold) {
            handleCircuitBreakerTrip(instance, circuitBreaker, recentFailures);
        } else {
            // Mark as unhealthy but don't remove yet
            if (instance.getStatus() == ServiceInstance.ServiceStatus.HEALTHY) {
                registryService.updateInstanceStatus(instance.getInstanceId(), ServiceInstance.ServiceStatus.UNHEALTHY);
                notificationService.sendServiceUnhealthyAlert(instance);
                log.warn("Service instance marked as unhealthy: instanceId={}, serviceName={}", 
                        instance.getInstanceId(), instance.getServiceName());
            }
        }
    }
    
    /**
     * Handle circuit breaker trip - remove service from registry
     */
    private void handleCircuitBreakerTrip(ServiceInstance instance, CircuitBreakerState circuitBreaker, int failureCount) {
        if (!circuitBreaker.isCircuitOpen()) {
            circuitBreaker.setCircuitOpen(true);
            
            log.error("Circuit breaker TRIPPED for instance: instanceId={}, serviceName={}, failures={}", 
                    instance.getInstanceId(), instance.getServiceName(), failureCount);
            
            // Send notification
            notificationService.sendCircuitBreakerTrippedAlert(
                    instance.getInstanceId(), 
                    instance.getServiceName(), 
                    failureCount
            );
            
            // Remove service from registry (Grade 8 requirement)
            log.info("Removing unhealthy service instance: instanceId={}, serviceName={}", 
                    instance.getInstanceId(), instance.getServiceName());
            
            notificationService.sendServiceRemovedAlert(instance, failureCount);
            registryService.deregister(instance.getInstanceId());
            
            // Clean up circuit breaker state
            circuitBreakers.remove(instance.getInstanceId());
            highLoadAlertSent.remove(instance.getInstanceId());
        }
    }
    
    /**
     * Remove services that haven't sent heartbeat in the configured timeout period
     */
    private void removeExpiredHeartbeats() {
        LocalDateTime expirationTime = LocalDateTime.now().minusNanos(heartbeatTimeout * 1_000_000);
        List<ServiceInstance> allInstances = registryService.getAllInstances();
        
        for (ServiceInstance instance : allInstances) {
            if (instance.getLastHeartbeat().isBefore(expirationTime)) {
                log.warn("Service instance heartbeat expired: instanceId={}, serviceName={}, lastHeartbeat={}", 
                        instance.getInstanceId(), instance.getServiceName(), instance.getLastHeartbeat());
                
                registryService.deregister(instance.getInstanceId());
                circuitBreakers.remove(instance.getInstanceId());
                highLoadAlertSent.remove(instance.getInstanceId());
                
                log.info("Removed service instance due to expired heartbeat: instanceId={}", 
                        instance.getInstanceId());
            }
        }
    }
    
    /**
     * Send high load alert only once per instance until load returns to normal
     */
    private void sendHighLoadAlertOnce(ServiceInstance instance) {
        String instanceId = instance.getInstanceId();
        if (!highLoadAlertSent.getOrDefault(instanceId, false)) {
            notificationService.sendHighLoadAlert(instance, loadThreshold);
            highLoadAlertSent.put(instanceId, true);
            log.warn("High load detected: instanceId={}, serviceName={}, load={}%", 
                    instanceId, instance.getServiceName(), instance.getCurrentLoad());
        }
    }
    
    /**
     * Get circuit breaker state for an instance (for monitoring/debugging)
     */
    public CircuitBreakerState getCircuitBreakerState(String instanceId) {
        return circuitBreakers.get(instanceId);
    }
}

