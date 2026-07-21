package fi.petri.springauction.notification;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidEventType;
import fi.petri.springauction.bid.BidRepository;
import fi.petri.springauction.result.AuctionFinalizationService;
import fi.petri.springauction.result.AuctionResultRepository;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Non-transactional on purpose: the notifier runs on {@code AFTER_COMMIT}, so the finalization
 * transaction must actually commit for any email to be sent. Committed rows are cleaned up per test.
 */
@SpringBootTest(properties = {
        "app.notification.base-url=http://localhost:8080",
        "app.notification.from-address=auctions@spring-auction.local"
})
@Import(TestcontainersConfiguration.class)
class AuctionEmailNotificationIntegrationTest {

    @MockitoSpyBean
    JavaMailSender mailSender;

    @Autowired
    AuctionFinalizationService finalizationService;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    AuctionResultRepository resultRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcClient jdbcClient;

    private final List<Auction> createdAuctions = new ArrayList<>();
    private final List<Bid> createdBids = new ArrayList<>();
    private final List<User> createdUsers = new ArrayList<>();
    private int userSeq = 0;

    @BeforeEach
    void stubSend() {
        // Spy wraps the real JavaMailSender (which creates real MimeMessages); only suppress the SMTP send.
        doNothing().when(mailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void cleanup() {
        // Append-only: finalization adds new version rows (same auction_ref), so purge every version by ref.
        createdAuctions.forEach(a -> {
            resultRepository.findByAuctionId(a.auctionRef()).ifPresent(resultRepository::delete);
            jdbcClient.sql("DELETE FROM auction WHERE auction_ref = :ref").param("ref", a.auctionRef()).update();
        });
        bidRepository.deleteAll(createdBids);
        userRepository.deleteAll(createdUsers);
    }

    private Auction endedAuction(String itemId, String startPrice) {
        Auction auction = auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Dell laptop", "A laptop", "laptops", AuctionType.FIRST_PRICE,
                AuctionLifecycleStatus.ACTIVE, new BigDecimal(startPrice), new BigDecimal(startPrice),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60),
                null, null, Instant.now()));
        createdAuctions.add(auction);
        return auction;
    }

    private User user() {
        User user = userRepository.save(new User(
                null, null, "bidder" + (++userSeq) + "@example.com", "Bidder " + userSeq, UserRole.USER, Instant.now()));
        createdUsers.add(user);
        return user;
    }

    private void placeBid(Long auctionId, Long userId, String amount) {
        createdBids.add(bidRepository.save(new Bid(null, auctionId, userId, BidEventType.PLACED,
                new BigDecimal(amount), userId, null, Instant.now())));
    }

    private static String contentOf(MimeMessage message) throws Exception {
        return message.getContent().toString();
    }

    @Test
    void winnerGetsPayLinkAndEachLoserGetsExactlyOneEmail() throws Exception {
        Auction auction = endedAuction("MAIL-1", "100");
        User winner = user();
        User loser1 = user();
        User loser2 = user();
        placeBid(auction.auctionRef(), winner.id(), "300");
        placeBid(auction.auctionRef(), loser1.id(), "150");
        placeBid(auction.auctionRef(), loser2.id(), "200");

        finalizationService.finalizeAuction(auction.auctionRef());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());
        List<MimeMessage> sent = captor.getAllValues();

        MimeMessage winMail = sent.stream()
                .filter(m -> recipient(m).equals(winner.email()))
                .findFirst().orElseThrow();
        assertEquals("Auction won", winMail.getSubject());
        String winBody = contentOf(winMail);
        assertTrue(winBody.contains("Dell laptop"), winBody);
        assertTrue(winBody.contains("http://localhost:8080/auctions/" + auction.auctionRef()), winBody);
        assertTrue(winBody.contains("300.00 EUR"), winBody);

        assertEquals(
                List.of(winner.email(), loser1.email(), loser2.email()).stream().sorted().toList(),
                sent.stream().map(AuctionEmailNotificationIntegrationTest::recipient).sorted().toList());
    }

    @Test
    void reFinalizingSendsNothingTheSecondTime() {
        Auction auction = endedAuction("MAIL-2", "100");
        User winner = user();
        placeBid(auction.auctionRef(), winner.id(), "150");

        finalizationService.finalizeAuction(auction.auctionRef());
        finalizationService.finalizeAuction(auction.auctionRef());

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
