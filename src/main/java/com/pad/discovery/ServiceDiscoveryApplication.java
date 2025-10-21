package com.pad.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Service Discovery microservice.
 * Provides service registration, health monitoring, and circuit breaker functionality.
 */
@SpringBootApplication
@EnableScheduling
public class ServiceDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDiscoveryApplication.class, args);
    }
}

