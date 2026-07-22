package fi.petri.springauction.notification;

/**
 * Transport seam for outgoing notification emails. Implementations are selected by the
 * {@code app.notification.transport} property: {@code smtp} (default, {@link SmtpEmailSender} → local
 * Mailpit or an SMTP relay) or {@code mailjet} ({@link MailjetEmailSender} → Mailjet HTTP API in GCP).
 * A send failure is expected to throw; {@link AuctionEmailNotifier} isolates each recipient.
 */
public interface EmailSender {

    void send(String to, String subject, String htmlBody);
}
