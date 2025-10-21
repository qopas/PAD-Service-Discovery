# Service Discovery Microservice

A comprehensive Spring Boot-based Service Discovery microservice that provides service registration, health monitoring, circuit breaker functionality, and load-based routing.

## Features

### Core Features (Grade 2-3)
- **Service Registration**: Register service instances with the registry
- **Service Discovery**: Find and retrieve service instances by name
- **Heartbeat Management**: Track service health with periodic heartbeats
- **Service Deregistration**: Remove services from the registry
- **Comprehensive Logging**: SLF4J logging for all operations

### Health Monitoring (Grade 5)
- **Scheduled Health Checks**: Automatic health checks every 30 seconds
- **Health Status Tracking**: Mark services as HEALTHY or UNHEALTHY
- **Heartbeat Expiration**: Automatically remove services with expired heartbeats (90 seconds)
- **Load Tracking**: Monitor service load from status endpoints
- **Log Download**: Download application logs via REST API

### Circuit Breaker (Grade 6 & 8)
- **Failure Tracking**: Track failures per service instance
- **Automatic Trip**: Trip circuit breaker after threshold failures
- **Automatic Removal**: Remove unhealthy services from registry when circuit breaker trips
- **Configurable Thresholds**: Customize failure count and time windows

### Load-Based Routing (Grade 7)
- **Load Information**: Track CPU/memory usage and request count
- **Load-Based Sorting**: Retrieve service instances sorted by load
- **Optimal Routing**: Direct traffic to least-loaded instances

### Notifications (Grade 9)
- **Telegram Integration**: Send alerts via Telegram Bot API
- **Health Alerts**: Notifications when services become unhealthy
- **Circuit Breaker Alerts**: Notifications when circuit breaker trips
- **High Load Alerts**: Notifications when load exceeds threshold
- **Removal Alerts**: Notifications when services are removed

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Web** (REST API)
- **Spring WebFlux** (Async HTTP calls)
- **Spring Actuator** (Health monitoring)
- **Lombok** (Code generation)
- **Maven** (Build tool)

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (optional, for containerized deployment)

### Building the Application

```bash
mvn clean package
```

### Running Locally

```bash
java -jar target/service-discovery-1.0.0.jar
```

The service will start on port **8761** by default.

### Running with Docker

```bash
# Build the Docker image
docker build -t service-discovery:latest .

# Run the container
docker run -p 8761:8761 service-discovery:latest
```

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
server:
  port: 8761

discovery:
  health-check:
    interval: 30000        # Health check interval (ms)
    timeout: 5000          # Health check timeout (ms)
    heartbeat-timeout: 90000  # Heartbeat expiration (ms)
  circuit-breaker:
    failure-threshold: 3   # Failures before circuit trips
    window-multiplier: 3.5 # Time window multiplier
  load-threshold: 80.0     # High load alert threshold (%)

telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN:}
  chat-id: ${TELEGRAM_CHAT_ID:}
  enabled: ${TELEGRAM_ENABLED:false}
```

### Environment Variables

- `TELEGRAM_BOT_TOKEN`: Telegram bot token for notifications
- `TELEGRAM_CHAT_ID`: Telegram chat ID for notifications
- `TELEGRAM_ENABLED`: Enable/disable Telegram notifications (default: false)

## API Endpoints

### Service Registration

**Register a Service Instance**
```http
POST /api/discovery/register
Content-Type: application/json

{
  "serviceName": "user-service",
  "serviceUrl": "http://localhost:8080",
  "instanceId": "user-service-1",
  "currentLoad": 25.5,
  "requestCount": 10
}
```

### Service Discovery

**Get Service Instances**
```http
GET /api/discovery/services/{serviceName}
```

**Get Service Instances Sorted by Load**
```http
GET /api/discovery/services/{serviceName}/by-load
```

**Get All Services**
```http
GET /api/discovery/services
```

### Health Management

**Update Heartbeat**
```http
POST /api/discovery/heartbeat
Content-Type: application/json

{
  "instanceId": "user-service-1",
  "currentLoad": 30.2,
  "requestCount": 15
}
```

**Deregister Service**
```http
DELETE /api/discovery/deregister/{instanceId}
```

### Logs

**Download Logs**
```http
GET /api/discovery/logs
```

**Get Recent Log Lines**
```http
GET /api/discovery/logs/recent?lines=100
```

### Health Check

**Service Health**
```http
GET /api/discovery/health
```

**Status Endpoint**
```http
GET /status
```

## Service Integration

### Registering Your Service

To register your service with the Service Discovery:

1. Send a POST request to `/api/discovery/register` on startup
2. Implement a `/status` endpoint that returns:
   ```json
   {
     "status": "UP",
     "load": 45.2,
     "requestCount": 20
   }
   ```
3. Send periodic heartbeats to `/api/discovery/heartbeat`

### Example Integration (Spring Boot)

```java
@Component
public class ServiceRegistrationService {
    
    @Value("${service.discovery.url}")
    private String discoveryUrl;
    
    @PostConstruct
    public void register() {
        RegisterRequest request = new RegisterRequest(
            "my-service",
            "http://localhost:8080",
            UUID.randomUUID().toString(),
            0.0,
            0
        );
        
        restTemplate.postForEntity(
            discoveryUrl + "/api/discovery/register",
            request,
            ApiResponse.class
        );
    }
    
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        HeartbeatRequest request = new HeartbeatRequest(
            instanceId,
            getCurrentLoad(),
            getRequestCount()
        );
        
        restTemplate.postForEntity(
            discoveryUrl + "/api/discovery/heartbeat",
            request,
            ApiResponse.class
        );
    }
}
```

## Circuit Breaker Behavior

The circuit breaker monitors service health and automatically removes unhealthy services:

1. **Health Checks**: Service Discovery calls `/status` on each service every 30 seconds
2. **Failure Tracking**: Failed health checks are recorded with timestamps
3. **Threshold**: After 3 failures within a time window, circuit breaker trips
4. **Automatic Removal**: Service is automatically removed from registry
5. **Notifications**: Alerts are sent via Telegram when circuit breaker trips

## Telegram Notifications

To enable Telegram notifications:

1. Create a Telegram bot using [@BotFather](https://t.me/botfather)
2. Get your chat ID
3. Set environment variables:
   ```bash
   export TELEGRAM_BOT_TOKEN=your_bot_token
   export TELEGRAM_CHAT_ID=your_chat_id
   export TELEGRAM_ENABLED=true
   ```

## Logging

Logs are written to `logs/service-discovery.log` and include:

- Service registration/deregistration events
- Health check results
- Circuit breaker events
- Heartbeat updates
- Error conditions

## CI/CD

The project includes GitHub Actions workflow for:

- Building the application with Maven
- Running tests
- Building Docker image
- Pushing to DockerHub with tags:
  - `latest` (for main branch)
  - `{commit-sha}` (for every commit)

## Contributing

1. Follow Spring Boot best practices
2. Use RESTful API design principles
3. Write comprehensive tests
4. Add logging for important operations
5. Update documentation for new features

## License

This project is part of PAD Team15 coursework.

