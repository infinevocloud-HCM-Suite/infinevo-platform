package com.infinevo.worker.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brevo (Sendinblue) implementation of {@link DeliveryClient} (W-20.2).
 *
 * <p>Uses the Brevo SMTP API (v3/smtp/email) with credential injected from the environment
 * via Key Vault (W-56), never committed in plaintext (closing DEBT-004).
 */
@Component
public class BrevoDeliveryClient implements DeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(BrevoDeliveryClient.class);

    private final String apiKey;
    private final String apiUrl;
    private final String fromEmail;
    private final String fromName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public BrevoDeliveryClient(
            @Value("${brevo.api-key:${brevo.apiKey}}") String apiKey,
            @Value("${brevo.api-url:${brevo.url:https://api.brevo.com/v3/smtp/email}}") String apiUrl,
            @Value("${brevo.from-email:${brevo.from.email:notifications@infinevocloud.com}}") String fromEmail,
            @Value("${brevo.from-name:${brevo.from.name:Infinevo}}") String fromName,
            ObjectMapper objectMapper) {
        this(
                apiKey,
                apiUrl,
                fromEmail,
                fromName,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                objectMapper);
    }

    BrevoDeliveryClient(
            String apiKey,
            String apiUrl,
            String fromEmail,
            String fromName,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.apiKey = Objects.requireNonNull(apiKey, "brevo api-key must not be null");
        this.apiUrl = Objects.requireNonNull(apiUrl, "brevo api-url must not be null");
        this.fromEmail = Objects.requireNonNull(fromEmail, "brevo from-email must not be null");
        this.fromName = Objects.requireNonNull(fromName, "brevo from-name must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        Objects.requireNonNull(toEmail, "toEmail must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");

        Map<String, Object> payload = Map.of(
                "sender",
                Map.of("name", fromName, "email", fromEmail),
                "to",
                List.of(Map.of("email", toEmail)),
                "subject",
                subject,
                // The body is HTML (W-20.1 escapes values for it); sent as text too, it would show the tags.
                "htmlContent",
                body);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw DeliveryException.permanentFailure("Failed to serialize Brevo email payload", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.debug("Email accepted by Brevo (status {})", status);
                return;
            }

            if (status == 429) {
                log.warn("Brevo rate limit exceeded (429)");
                throw DeliveryException.transientFailure("Brevo rate limit exceeded (429)");
            }

            if (status >= 500) {
                log.warn("Brevo server error ({})", status);
                throw DeliveryException.transientFailure("Brevo server error (" + status + ")");
            }

            // 4xx client errors are permanent rejections (e.g. invalid recipient, bad request)
            // Recipient addresses and Brevo's echo of the request stay out of the log and the row: the
            // status code says enough to act on.
            log.error("Brevo rejected the email ({})", status);
            throw DeliveryException.permanentFailure("Brevo rejected the email (" + status + ")");

        } catch (IOException e) {
            log.warn("Network error communicating with Brevo: {}", e.getMessage());
            throw DeliveryException.transientFailure("Network error communicating with Brevo: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DeliveryException.transientFailure("Interrupted while sending email via Brevo", e);
        }
    }
}
