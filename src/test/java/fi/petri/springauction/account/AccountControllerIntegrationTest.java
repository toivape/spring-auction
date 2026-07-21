package fi.petri.springauction.account;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidEventType;
import fi.petri.springauction.bid.BidRepository;
import fi.petri.springauction.result.AuctionResult;
import fi.petri.springauction.result.AuctionResultRepository;
import fi.petri.springauction.result.ResultStatus;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountControllerIntegrationTest {

    private static final String SUBJECT = "account-test-subject";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    AuctionResultRepository resultRepository;

    @Autowired
    UserRepository userRepository;

    private User me() {
        return userRepository.findByGoogleSubjectId(SUBJECT).orElseGet(() -> userRepository.save(
                new User(null, SUBJECT, "me@example.com", "Me", UserRole.USER, Instant.now())));
    }

    private User otherUser() {
        return userRepository.save(new User(null, "other-" + Instant.now().getNano(),
                "other@example.com", "Other", UserRole.USER, Instant.now()));
    }

    private RequestPostProcessor asMe() {
        me();
        return SecurityMockMvcRequestPostProcessors.oidcLogin()
                .idToken(token -> token.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Auction activeAuction(String itemId, String title) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, title, "Dell laptop", "laptops",
                AuctionType.FIRST_PRICE, AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                "EUR", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), null, null, Instant.now()));
    }

    private Auction soldAuction(String itemId, String title, Long winnerId, String price, boolean paid) {
        Auction auction = auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, title, "Dell laptop", "laptops",
                AuctionType.FIRST_PRICE, AuctionLifecycleStatus.SOLD, BigDecimal.valueOf(100), new BigDecimal(price),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60), null, null, Instant.now()));
        resultRepository.save(new AuctionResult(
                null, auction.auctionRef(), ResultStatus.SOLD, winnerId, new BigDecimal(price),
                Instant.now(), paid ? Instant.now() : null, null, null, null, null));
        return auction;
    }

    private void bid(Long auctionRef, Long userId, String amount, BidEventType type) {
        bidRepository.save(new Bid(null, auctionRef, userId, type, new BigDecimal(amount), userId, null, Instant.now()));
    }

    @Test
    void accountShowsOngoingWonAndLostWithTotals() throws Exception {
        User me = me();
        User other = otherUser();

        Auction ongoing = activeAuction("IB-ONG", "Ongoing item");
        bid(ongoing.auctionRef(), me.id(), "120.00", BidEventType.PLACED);

        Auction won = soldAuction("IB-WON", "Won item", me.id(), "300.00", false);

        Auction lost = soldAuction("IB-LOST", "Lost item", other.id(), "777.00", false);
        bid(lost.auctionRef(), me.id(), "150.00", BidEventType.PLACED);

        mockMvc.perform(get("/account").with(asMe()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 ongoing · 1 won · 1 lost")))
                .andExpect(content().string(containsString("Ongoing item")))
                .andExpect(content().string(containsString("Won item")))
                .andExpect(content().string(containsString("Pay now")))
                .andExpect(content().string(containsString("Lost item")))
                .andExpect(content().string(containsString("Total won: 300.00 EUR")))
                // sealed: the winning price of a lost auction is never shown
                .andExpect(content().string(not(containsString("777.00"))));
    }

    @Test
    void wonPaidShowsPaymentReceivedNotPayNow() throws Exception {
        User me = me();
        soldAuction("IB-PAID", "Paid item", me.id(), "250.00", true);

        mockMvc.perform(get("/account").with(asMe()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payment received")))
                .andExpect(content().string(not(containsString("Pay now"))));
    }

    @Test
    void lostIncludesAuctionsWhereMyBidWasWithdrawn() throws Exception {
        User me = me();
        User other = otherUser();

        Auction lost = soldAuction("IB-WD", "Withdrawn then lost", other.id(), "500.00", false);
        bid(lost.auctionRef(), me.id(), "150.00", BidEventType.PLACED);
        bid(lost.auctionRef(), me.id(), "150.00", BidEventType.WITHDRAWN);

        mockMvc.perform(get("/account").with(asMe()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Withdrawn then lost")))
                .andExpect(content().string(containsString("1 lost")));
    }
}
