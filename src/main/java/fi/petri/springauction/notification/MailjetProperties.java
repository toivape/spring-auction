package fi.petri.springauction.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mailjet API credentials, used only when {@code app.notification.transport=mailjet}. Supplied via env
 * (AWS Secrets Manager → ECS task env) in production; blank locally where the SMTP transport is the default.
 */
@ConfigurationProperties(prefix = "app.notification.mailjet")
public record MailjetProperties(String apiKey, String secretKey) {
}
