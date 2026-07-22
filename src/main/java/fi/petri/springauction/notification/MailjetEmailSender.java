package fi.petri.springauction.notification;

import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mailjet HTTP API transport (Send API v3.1) — used in GCP, where outbound SMTP port 25 is blocked and
 * there is no native email service. Active when {@code app.notification.transport=mailjet}; the SMTP
 * transport ({@link SmtpEmailSender}) is the default everywhere else.
 */
@Component
@ConditionalOnProperty(name = "app.notification.transport", havingValue = "mailjet")
public class MailjetEmailSender implements EmailSender {

    private final MailjetClient client;
    private final NotificationProperties properties;

    public MailjetEmailSender(MailjetClient client, NotificationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        MailjetRequest request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray().put(new JSONObject()
                        .put(Emailv31.Message.FROM, new JSONObject().put("Email", properties.fromAddress()))
                        .put(Emailv31.Message.TO, new JSONArray().put(new JSONObject().put("Email", to)))
                        .put(Emailv31.Message.SUBJECT, subject)
                        .put(Emailv31.Message.HTMLPART, htmlBody)));
        try {
            MailjetResponse response = client.post(request);
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException("Mailjet send to " + to + " failed: HTTP "
                        + response.getStatus() + " " + response.getRawResponseContent());
            }
        } catch (MailjetException e) {
            throw new IllegalStateException("Mailjet send to " + to + " failed", e);
        }
    }
}
