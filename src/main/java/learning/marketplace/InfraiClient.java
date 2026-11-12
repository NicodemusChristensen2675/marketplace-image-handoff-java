package learning.marketplace;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class InfraiClient {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> post(String path, String contentType, byte[] body, String idempotencyKey) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = send(request);
            Map<String, Object> envelope = envelope(response.body(), response.statusCode());
            if (response.statusCode() == 429) {
                pause(response, attempt);
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) throw apiError(envelope, response.statusCode());
            if (response.statusCode() >= 500) {
                throw new InfraiException("TRANSPORT_ERROR", "Remote service response was not successful", response.statusCode());
            }
            Object data = envelope.get("data");
            if (!(data instanceof Map<?, ?> raw)) throw new IllegalStateException("Envelope data must be an object");
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) raw;
            return typed;
        }
        throw new InfraiException("RATE_LIMITED", "Request retry budget was exhausted", 429);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT_ERROR", e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Request interrupted", 0);
        }
    }

    private static Map<String, Object> envelope(String body, int status) {
        Object parsed;
        try {
            parsed = Json.parse(body);
        } catch (RuntimeException e) {
            throw new InfraiException("INVALID_RESPONSE", "Response was not a JSON envelope", status);
        }
        if (!(parsed instanceof Map<?, ?> raw)) throw new InfraiException("INVALID_RESPONSE", "Response envelope must be an object", status);
        @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) raw;
        return typed;
    }

    private static InfraiException apiError(Map<String, Object> envelope, int status) {
        Object value = envelope.get("error");
        if (value instanceof Map<?, ?> error) {
            Object codeValue = error.containsKey("code") ? error.get("code") : "API_ERROR";
            Object messageValue = error.containsKey("message") ? error.get("message") : "Request rejected";
            String code = String.valueOf(codeValue);
            String message = String.valueOf(messageValue);
            return new InfraiException(code, message, status);
        }
        return new InfraiException("API_ERROR", "Request rejected", status);
    }

    private static void pause(HttpResponse<?> response, int attempt) {
        long seconds = response.headers().firstValue("Retry-After")
                .flatMap(value -> { try { return java.util.Optional.of(Long.parseLong(value)); } catch (NumberFormatException e) { return java.util.Optional.empty(); } })
                .orElse(1L << attempt);
        try {
            Thread.sleep(Math.min(seconds, 30) * 1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Backoff interrupted", 0);
        }
    }
}
