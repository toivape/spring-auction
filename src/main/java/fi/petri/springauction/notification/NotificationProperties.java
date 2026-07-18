package fi.petri.springauction.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl     absolute app base URL used to build the payment deep link in win emails
 *                    (emails need an absolute URL, unlike in-app relative links)
 * @param fromAddress the {@code From:} address on outgoing notification emails
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(String baseUrl, String fromAddress) {
}
