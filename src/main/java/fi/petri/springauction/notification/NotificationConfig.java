package fi.petri.springauction.notification;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({NotificationProperties.class, MailjetProperties.class})
public class NotificationConfig {

    /** Only created when the Mailjet transport is selected; otherwise the SMTP path needs no client. */
    @Bean
    @ConditionalOnProperty(name = "app.notification.transport", havingValue = "mailjet")
    MailjetClient mailjetClient(MailjetProperties properties) {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.secretKey())) {
            throw new IllegalStateException(
                    "app.notification.transport=mailjet requires MAILJET_API_KEY and MAILJET_SECRET_KEY to be set");
        }
        return new MailjetClient(ClientOptions.builder()
                .apiKey(properties.apiKey())
                .apiSecretKey(properties.secretKey())
                .build());
    }
}
