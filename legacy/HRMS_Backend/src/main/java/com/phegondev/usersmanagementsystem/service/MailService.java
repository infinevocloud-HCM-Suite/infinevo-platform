package com.phegondev.usersmanagementsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class MailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.from.email}")
    private String fromEmail;

    @Value("${brevo.from.name}")
    private String fromName;

    private static final HttpClient client = HttpClient.newHttpClient();

    // Build JSON array string for CC list
    private String buildCcJson(List<String> ccList) {
        if (ccList == null || ccList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\"cc\": [");
        for (int i = 0; i < ccList.size(); i++) {
            sb.append("{ \"email\": \"").append(ccList.get(i)).append("\", \"name\": \"CC\" }");
            if (i < ccList.size() - 1) sb.append(",");
        }
        sb.append("],");
        return sb.toString();
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, null, subject, body);
    }

    public void sendEmail(String to, List<String> cc, String subject, String body) {
        try {
            String htmlBody = body.replace("\n", "<br>").replace("\"", "\\\"");
            String textBody = body.replace("\"", "\\\"").replace("\n", "\\n");

            String ccJson = buildCcJson(cc);

            String payload = """
            {
              "sender": {
                "name": "%s",
                "email": "%s"
              },
              "to": [
                {
                  "email": "%s",
                  "name": "User"
                }
              ],
              %s
              "subject": "%s",
              "htmlContent": "%s",
              "textContent": "%s"
            }
            """.formatted(
                    fromName,
                    fromEmail,
                    to,
                    ccJson,
                    subject,
                    htmlBody,
                    textBody
            );

            System.out.println("From Email: " + fromEmail);
            System.out.println("To Email: " + to);
            System.out.println("Subject: " + subject);
            System.out.println("Payload: " + payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Response Body: " + response.body());

        } catch (Exception e) {
            System.err.println("❌ Email sending failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendStatusUpdate(String to, String status) {
        String subject = "Timesheet Status Update";
        String body = "Your timesheet status has been updated to: " + status;
        sendEmail(to, subject, body);
    }
}
