package fi.petri.springauction.notification;

import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mailjet HTTP API transport (Send API v3.1) — used in GCP, where outbound SMTP port 25 is blocked and
 * there is no native email service. Active when {@code app.notification.transport=mailjet}; the SMTP
 * transport ({@link SmtpEmailSender}) is the default everywhere else.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notification.transport", havingValue = "mailjet")
public class MailjetEmailSender implements EmailSender {

    private final MailjetClient client;
    private final NotificationProperties properties;

    public MailjetEmailSender(MailjetClient client, NotificationProperties properties) {
        this.client = client;
        this.properties = properties;
        log.info("MailjetEmailSender initialized");
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        MailjetRequest request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray().put(new JSONObject()
                        .put(Emailv31.Message.FROM, new JSONObject().put("Email", properties.fromAddress()))
                        .put(Emailv31.Message.TO, new JSONArray().put(new JSONObject().put("Email", to)))
                        .put(Emailv31.Message.SUBJECT, subject)
                        .put(Emailv31.Message.HTMLPART, htmlBody)));
        MailjetResponse response;
        try {
            response = client.post(request);
        } catch (MailjetException e) {
            throw new IllegalStateException("Mailjet send to " + to + " failed", e);
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException("Mailjet send to " + to + " failed: HTTP "
                    + response.getStatus() + " " + response.getRawResponseContent());
        }
        // Send API v3.1 can return HTTP 200 while a message is rejected — the per-message outcome is in
        // Messages[].Status. Treat anything but "success" as a failure so a rejected email isn't logged as sent.
        JSONArray messages = new JSONObject(response.getRawResponseContent()).optJSONArray("Messages");
        if (messages == null || messages.isEmpty()) {
            throw new IllegalStateException("Mailjet send to " + to + " returned no message status: "
                    + response.getRawResponseContent());
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            if (!"success".equalsIgnoreCase(msg.optString("Status"))) {
                throw new IllegalStateException("Mailjet rejected message to " + to + ": " + msg);
            }
        }
    }
}
