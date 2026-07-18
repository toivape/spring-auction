package fi.petri.springauction.notification;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends win/lose emails when an auction is finalized. Runs {@code AFTER_COMMIT} so a rolled-back or
 * already-finalized finalization sends nothing (send-once for free) and an email failure can never block
 * finalization. Each send is isolated: one bad address doesn't stop the rest.
 */
@Component
public class AuctionEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(AuctionEmailNotifier.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final UserRepository userRepository;
    private final NotificationProperties properties;

    public AuctionEmailNotifier(JavaMailSender mailSender, SpringTemplateEngine templateEngine,
                                UserRepository userRepository, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionFinalized(AuctionFinalizedEvent event) {
        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(event.bidderUserIds()).forEach(user -> usersById.put(user.id(), user));

        User winner = usersById.get(event.winnerUserId());
        if (winner != null) {
            send(winner.email(), "You won the auction: " + event.auctionTitle(), "email/win", winContext(event, winner));
        }
        for (Long loserId : event.loserUserIds()) {
            User loser = usersById.get(loserId);
            if (loser != null) {
                send(loser.email(), "Auction ended: " + event.auctionTitle(), "email/lose", loseContext(event, loser));
            }
        }
    }

    private Context winContext(AuctionFinalizedEvent event, User winner) {
        Context context = new Context();
        context.setVariable("displayName", winner.displayName());
        context.setVariable("title", event.auctionTitle());
        context.setVariable("price", event.winningPrice());
        context.setVariable("currency", event.currency());
        context.setVariable("payUrl", properties.baseUrl() + "/auctions/" + event.auctionId());
        return context;
    }

    private Context loseContext(AuctionFinalizedEvent event, User loser) {
        Context context = new Context();
        context.setVariable("displayName", loser.displayName());
        context.setVariable("title", event.auctionTitle());
        return context;
    }

    private void send(String to, String subject, String template, Context context) {
        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.fromAddress());
            helper.setTo(to);
            helper.setSubject(sanitizeHeader(subject));
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent {} email to {}", template, to);
        } catch (Exception e) {
            log.warn("Failed to send {} email to {}: {}", template, to, e.getMessage(), e);
        }
    }

    /** Strip CR/LF so an admin/ingest-controlled auction title can't inject extra mail headers. */
    private static String sanitizeHeader(String value) {
        return value.replaceAll("[\\r\\n]", " ");
    }
}
