package com.pad.discovery.service;

import com.pad.discovery.model.ServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending notifications via Discord API.
 * Sends alerts for service health changes, circuit breaker events, and high load.
 */
@Service
@Slf4j
public class NotificationService {

    private final WebClient webClient;
    private final String notificationApiUrl;
    private final String recipientEmail;
    private final boolean enabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NotificationService(
            WebClient.Builder webClientBuilder,
            @Value("${notification.api-url:http://gateway:8000/api/v1/notifications/discord/dm-by-email}") String notificationApiUrl,
            @Value("${notification.recipient-email}") String recipientEmail,
            @Value("${notification.enabled:true}") boolean enabled) {

        this.webClient = webClientBuilder.build();
        this.notificationApiUrl = notificationApiUrl;
        this.recipientEmail = recipientEmail;
        this.enabled = enabled;

        if (enabled && recipientEmail.isEmpty()) {
            log.warn("Discord notifications enabled but recipient email not configured");
        }
    }

    /**
     * Send notification when a service becomes unhealthy
     */
    public void sendServiceUnhealthyAlert(ServiceInstance instance) {
        String message = String.format(
                "⚠️ **SERVICE UNHEALTHY ALERT**\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Status: %s\n" +
                        "Timestamp: %s",
                instance.getServiceName(),
                instance.getInstanceId(),
                instance.getServiceUrl(),
                instance.getStatus(),
                LocalDateTime.now().format(FORMATTER)
        );

        sendDiscordNotification(message);
        log.info("Sent unhealthy alert for service: {}, instanceId: {}",
                instance.getServiceName(), instance.getInstanceId());
    }

    /**
     * Send notification when a service is removed by circuit breaker
     */
    public void sendServiceRemovedAlert(ServiceInstance instance, int failureCount) {
        String message = String.format(
                "🔴 **SERVICE REMOVED BY CIRCUIT BREAKER**\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Failure Count: %d\n" +
                        "Timestamp: %s\n\n" +
                        "The service has been automatically removed from the registry due to repeated failures.",
                instance.getServiceName(),
                instance.getInstanceId(),
                instance.getServiceUrl(),
                failureCount,
                LocalDateTime.now().format(FORMATTER)
        );

        sendDiscordNotification(message);
        log.info("Sent removal alert for service: {}, instanceId: {}, failures: {}",
                instance.getServiceName(), instance.getInstanceId(), failureCount);
    }

    /**
     * Send notification when service load exceeds threshold
     */
    public void sendHighLoadAlert(ServiceInstance instance, double loadThreshold) {
        String message = String.format(
                "📊 **HIGH LOAD ALERT**\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Current Load: %.2f%%\n" +
                        "Threshold: %.2f%%\n" +
                        "Request Count: %d\n" +
                        "Timestamp: %s",
                instance.getServiceName(),
                instance.getInstanceId(),
                instance.getServiceUrl(),
                instance.getCurrentLoad(),
                loadThreshold,
                instance.getRequestCount() != null ? instance.getRequestCount() : 0,
                LocalDateTime.now().format(FORMATTER)
        );

        sendDiscordNotification(message);
        log.info("Sent high load alert for service: {}, instanceId: {}, load: {}%",
                instance.getServiceName(), instance.getInstanceId(), instance.getCurrentLoad());
    }

    /**
     * Send notification when circuit breaker trips
     */
    public void sendCircuitBreakerTrippedAlert(String instanceId, String serviceName, int failureCount) {
        String message = String.format(
                "⚡ **CIRCUIT BREAKER TRIPPED**\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Failure Count: %d\n" +
                        "Timestamp: %s\n\n" +
                        "The circuit breaker has been activated for this service instance.",
                serviceName,
                instanceId,
                failureCount,
                LocalDateTime.now().format(FORMATTER)
        );

        sendDiscordNotification(message);
        log.info("Sent circuit breaker tripped alert for service: {}, instanceId: {}",
                serviceName, instanceId);
    }

    /**
     * Send a message via Discord notification API
     */
    private void sendDiscordNotification(String message) {
        if (!enabled) {
            log.debug("Discord notifications disabled, skipping message: {}", message);
            return;
        }

        if (recipientEmail.isEmpty()) {
            log.warn("Recipient email not configured, cannot send notification");
            return;
        }

        Map<String, String> request = new HashMap<>();
        request.put("message", message);
        request.put("email", recipientEmail);
        request.put("author", "ServiceDiscovery");

        webClient.post()
                .uri(notificationApiUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(error -> {
                    log.error("Failed to send Discord notification: {}", error.getMessage());
                    return Mono.empty();
                })
                .subscribe(
                        response -> log.debug("Discord notification sent successfully: {}", response),
                        error -> log.error("Error in Discord notification: {}", error.getMessage())
                );
    }
}