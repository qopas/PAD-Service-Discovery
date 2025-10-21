package com.pad.discovery.service;

import com.pad.discovery.model.ServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service Registry that manages all registered service instances.
 * Uses thread-safe ConcurrentHashMap for concurrent access.
 */
@Service
@Slf4j
public class RegistryService {
    
    // Map of service name to list of service instances
    private final Map<String, List<ServiceInstance>> serviceRegistry = new ConcurrentHashMap<>();
    
    /**
     * Register a new service instance in the registry
     * 
     * @param instance Service instance to register
     * @return Registered service instance
     */
    public ServiceInstance register(ServiceInstance instance) {
        log.info("Registering service instance: serviceName={}, instanceId={}, serviceUrl={}", 
                instance.getServiceName(), instance.getInstanceId(), instance.getServiceUrl());
        
        instance.setLastHeartbeat(LocalDateTime.now());
        instance.setStatus(ServiceInstance.ServiceStatus.HEALTHY);
        
        serviceRegistry.compute(instance.getServiceName(), (key, instances) -> {
            if (instances == null) {
                instances = Collections.synchronizedList(new ArrayList<>());
            }
            
            // Remove existing instance with same ID if present
            instances.removeIf(existing -> existing.getInstanceId().equals(instance.getInstanceId()));
            instances.add(instance);
            
            log.info("Service instance registered successfully: serviceName={}, instanceId={}, totalInstances={}", 
                    instance.getServiceName(), instance.getInstanceId(), instances.size());
            
            return instances;
        });
        
        return instance;
    }
    
    /**
     * Get all instances of a specific service
     * 
     * @param serviceName Name of the service
     * @return List of service instances
     */
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        log.debug("Retrieving instances for service: {}", serviceName);
        List<ServiceInstance> instances = serviceRegistry.getOrDefault(serviceName, Collections.emptyList());
        log.debug("Found {} instances for service: {}", instances.size(), serviceName);
        return new ArrayList<>(instances);
    }
    
    /**
     * Get all instances sorted by load (lowest to highest)
     * 
     * @param serviceName Name of the service
     * @return List of service instances sorted by load
     */
    public List<ServiceInstance> getServiceInstancesByLoad(String serviceName) {
        log.debug("Retrieving instances for service sorted by load: {}", serviceName);
        List<ServiceInstance> instances = getServiceInstances(serviceName);
        
        return instances.stream()
                .filter(instance -> instance.getStatus() == ServiceInstance.ServiceStatus.HEALTHY)
                .sorted(Comparator.comparing(
                        instance -> instance.getCurrentLoad() != null ? instance.getCurrentLoad() : 0.0
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * Deregister a service instance from the registry
     * 
     * @param instanceId Unique identifier of the instance to deregister
     * @return true if instance was found and removed, false otherwise
     */
    public boolean deregister(String instanceId) {
        log.info("Deregistering service instance: instanceId={}", instanceId);
        
        for (Map.Entry<String, List<ServiceInstance>> entry : serviceRegistry.entrySet()) {
            List<ServiceInstance> instances = entry.getValue();
            boolean removed = instances.removeIf(instance -> instance.getInstanceId().equals(instanceId));
            
            if (removed) {
                log.info("Service instance deregistered successfully: instanceId={}, serviceName={}, remainingInstances={}", 
                        instanceId, entry.getKey(), instances.size());
                
                // Clean up empty lists
                if (instances.isEmpty()) {
                    serviceRegistry.remove(entry.getKey());
                    log.info("Service {} has no more instances, removed from registry", entry.getKey());
                }
                
                return true;
            }
        }
        
        log.warn("Service instance not found for deregistration: instanceId={}", instanceId);
        return false;
    }
    
    /**
     * Update the heartbeat timestamp for a service instance
     * 
     * @param instanceId Unique identifier of the instance
     * @param currentLoad Current load on the service (optional)
     * @param requestCount Current request count (optional)
     * @return true if instance was found and updated, false otherwise
     */
    public boolean updateHeartbeat(String instanceId, Double currentLoad, Integer requestCount) {
        log.debug("Updating heartbeat for instance: instanceId={}", instanceId);
        
        for (List<ServiceInstance> instances : serviceRegistry.values()) {
            for (ServiceInstance instance : instances) {
                if (instance.getInstanceId().equals(instanceId)) {
                    instance.setLastHeartbeat(LocalDateTime.now());
                    
                    if (currentLoad != null) {
                        instance.setCurrentLoad(currentLoad);
                    }
                    if (requestCount != null) {
                        instance.setRequestCount(requestCount);
                    }
                    
                    log.debug("Heartbeat updated successfully: instanceId={}, load={}, requests={}", 
                            instanceId, currentLoad, requestCount);
                    return true;
                }
            }
        }
        
        log.warn("Service instance not found for heartbeat update: instanceId={}", instanceId);
        return false;
    }
    
    /**
     * Get all registered services with their instances
     * 
     * @return Map of service names to their instances
     */
    public Map<String, List<ServiceInstance>> getAllServices() {
        log.debug("Retrieving all registered services");
        Map<String, List<ServiceInstance>> allServices = new HashMap<>();
        
        serviceRegistry.forEach((serviceName, instances) -> {
            allServices.put(serviceName, new ArrayList<>(instances));
        });
        
        log.debug("Retrieved {} services with total {} instances", 
                allServices.size(), 
                allServices.values().stream().mapToInt(List::size).sum());
        
        return allServices;
    }
    
    /**
     * Get a specific service instance by ID
     * 
     * @param instanceId Unique identifier of the instance
     * @return Service instance if found, null otherwise
     */
    public ServiceInstance getInstanceById(String instanceId) {
        for (List<ServiceInstance> instances : serviceRegistry.values()) {
            for (ServiceInstance instance : instances) {
                if (instance.getInstanceId().equals(instanceId)) {
                    return instance;
                }
            }
        }
        return null;
    }
    
    /**
     * Update the status of a service instance
     * 
     * @param instanceId Unique identifier of the instance
     * @param status New status
     * @return true if instance was found and updated, false otherwise
     */
    public boolean updateInstanceStatus(String instanceId, ServiceInstance.ServiceStatus status) {
        ServiceInstance instance = getInstanceById(instanceId);
        if (instance != null) {
            ServiceInstance.ServiceStatus oldStatus = instance.getStatus();
            instance.setStatus(status);
            log.info("Service instance status updated: instanceId={}, oldStatus={}, newStatus={}", 
                    instanceId, oldStatus, status);
            return true;
        }
        return false;
    }
    
    /**
     * Get all service instances across all services
     * 
     * @return List of all service instances
     */
    public List<ServiceInstance> getAllInstances() {
        return serviceRegistry.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}

