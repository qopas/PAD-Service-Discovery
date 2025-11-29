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
 * Service for sending notifications via Telegram Bot API.
 * Sends alerts for service health changes, circuit breaker events, and high load.
 */
@Service
@Slf4j
public class NotificationService {

    private final WebClient webClient;
    private final String botToken;
    private final String chatId;
    private final boolean enabled;
    private final String telegramApiUrl;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NotificationService(
            WebClient.Builder webClientBuilder,
            @Value("${notification.telegram.bot-token}") String botToken,
            @Value("${notification.telegram.chat-id}") String chatId,
            @Value("${notification.enabled:true}") boolean enabled) {

        this.webClient = webClientBuilder.build();
        this.botToken = botToken;
        this.chatId = chatId;
        this.enabled = enabled;
        this.telegramApiUrl = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);

        if (enabled && (botToken.isEmpty() || chatId.isEmpty())) {
            log.warn("Telegram notifications enabled but bot token or chat ID not configured");
        }
    }

    /**
     * Send notification when a service becomes unhealthy
     */
    public void sendServiceUnhealthyAlert(ServiceInstance instance) {
        String message = String.format(
                "⚠️ *SERVICE UNHEALTHY ALERT*\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Status: %s\n" +
                        "Timestamp: %s",
                escapeMarkdown(instance.getServiceName()),
                escapeMarkdown(instance.getInstanceId()),
                escapeMarkdown(instance.getServiceUrl()),
                instance.getStatus(),
                LocalDateTime.now().format(FORMATTER)
        );

        sendTelegramNotification(message);
        log.info("Sent unhealthy alert for service: {}, instanceId: {}",
                instance.getServiceName(), instance.getInstanceId());
    }

    /**
     * Send notification when a service is removed by circuit breaker
     */
    public void sendServiceRemovedAlert(ServiceInstance instance, int failureCount) {
        String message = String.format(
                "🔴 *SERVICE REMOVED BY CIRCUIT BREAKER*\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Failure Count: %d\n" +
                        "Timestamp: %s\n\n" +
                        "The service has been automatically removed from the registry due to repeated failures.",
                escapeMarkdown(instance.getServiceName()),
                escapeMarkdown(instance.getInstanceId()),
                escapeMarkdown(instance.getServiceUrl()),
                failureCount,
                LocalDateTime.now().format(FORMATTER)
        );

        sendTelegramNotification(message);
        log.info("Sent removal alert for service: {}, instanceId: {}, failures: {}",
                instance.getServiceName(), instance.getInstanceId(), failureCount);
    }

    /**
     * Send notification when service load exceeds threshold
     */
    public void sendHighLoadAlert(ServiceInstance instance, double loadThreshold) {
        String message = String.format(
                "📊 *HIGH LOAD ALERT*\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Service URL: `%s`\n" +
                        "Current Load: %.2f%%\n" +
                        "Threshold: %.2f%%\n" +
                        "Request Count: %d\n" +
                        "Timestamp: %s",
                escapeMarkdown(instance.getServiceName()),
                escapeMarkdown(instance.getInstanceId()),
                escapeMarkdown(instance.getServiceUrl()),
                instance.getCurrentLoad(),
                loadThreshold,
                instance.getRequestCount() != null ? instance.getRequestCount() : 0,
                LocalDateTime.now().format(FORMATTER)
        );

        sendTelegramNotification(message);
        log.info("Sent high load alert for service: {}, instanceId: {}, load: {}%",
                instance.getServiceName(), instance.getInstanceId(), instance.getCurrentLoad());
    }

    /**
     * Send notification when circuit breaker trips
     */
    public void sendCircuitBreakerTrippedAlert(String instanceId, String serviceName, int failureCount) {
        String message = String.format(
                "⚡ *CIRCUIT BREAKER TRIPPED*\n\n" +
                        "Service: `%s`\n" +
                        "Instance ID: `%s`\n" +
                        "Failure Count: %d\n" +
                        "Timestamp: %s\n\n" +
                        "The circuit breaker has been activated for this service instance.",
                escapeMarkdown(serviceName),
                escapeMarkdown(instanceId),
                failureCount,
                LocalDateTime.now().format(FORMATTER)
        );

        sendTelegramNotification(message);
        log.info("Sent circuit breaker tripped alert for service: {}, instanceId: {}",
                serviceName, instanceId);
    }

    /**
     * Send a message via Telegram Bot API
     */
    private void sendTelegramNotification(String message) {
        if (!enabled) {
            log.debug("Telegram notifications disabled, skipping message: {}", message);
            return;
        }

        if (botToken.isEmpty() || chatId.isEmpty()) {
            log.warn("Bot token or chat ID not configured, cannot send notification");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", message);
        request.put("parse_mode", "Markdown");
        request.put("disable_web_page_preview", true);

        webClient.post()
                .uri(telegramApiUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(error -> {
                    log.error("Failed to send Telegram notification: {}", error.getMessage());
                    return Mono.empty();
                })
                .subscribe(
                        response -> log.debug("Telegram notification sent successfully: {}", response),
                        error -> log.error("Error in Telegram notification: {}", error.getMessage())
                );
    }

    /**
     * Escape special characters for Telegram Markdown
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // Escape special Markdown characters for Telegram
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}