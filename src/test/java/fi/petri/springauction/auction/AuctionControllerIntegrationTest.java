package fi.petri.springauction.auction;

import fi.petri.springauction.TestcontainersConfiguration;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuctionControllerIntegrationTest {

    private static final String SUBJECT = "test-google-subject";

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    AuctionResultRepository resultRepository;

    private Auction activeAuction(String itemId, Instant startsAt, Instant endsAt) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Active auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                "EUR", startsAt, endsAt, null, null, Instant.now()));
    }

    private Auction draftAuction(String itemId) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, null, "Dell laptop", "laptops", null,
                AuctionLifecycleStatus.DRAFT, BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                "EUR", null, null, null, null, Instant.now()));
    }

    private User bidderUser() {
        return userRepository.findByGoogleSubjectId(SUBJECT).orElseGet(() -> userRepository.save(
                new User(null, SUBJECT, "bidder@example.com", "Bidder", UserRole.USER, Instant.now())));
    }

    private RequestPostProcessor asBidder() {
        bidderUser();
        return SecurityMockMvcRequestPostProcessors.oidcLogin()
                .idToken(token -> token.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Auction soldAuction(String itemId, Long winnerUserId, String price) {
        Auction auction = auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Sold auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.SOLD, BigDecimal.valueOf(100), new BigDecimal(price),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60),
                null, null, Instant.now()));
        resultRepository.save(new AuctionResult(
                null, auction.auctionRef(), ResultStatus.SOLD, winnerUserId, new BigDecimal(price),
                Instant.now(), null, null, null, null, null));
        return auction;
    }

    @Test
    void anonymousRequestToRootRedirectsToGoogleLogin() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(302, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.contains("/oauth2/authorization/google"));
    }

    @Test
    void marketplaceListsOnlyActiveAuctions() throws Exception {
        Auction active = activeAuction("IB-A1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        draftAuction("IB-A2");

        mockMvc.perform(get("/").with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(active.title())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IB-A2"))));
    }

    @Test
    void marketplaceShowsMyBidAmountWhenIHavePlacedOne() throws Exception {
        Auction bidOn = activeAuction("IB-A3", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        Auction notBidOn = activeAuction("IB-A4", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", bidOn.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/").with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("150.00")));
    }

    @Test
    void auctionDetailShowsNoBidInitially() throws Exception {
        Auction auction = activeAuction("IB-B1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You haven't placed a bid yet")));
    }

    @Test
    void placingABidPersistsIt() throws Exception {
        Auction auction = activeAuction("IB-C1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.auctionRef()));

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        var bid = bidRepository.findCurrentBid(auction.auctionRef(), bidder.id()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(bid.amount()));
        assertEquals(BidEventType.PLACED, bid.eventType());
    }

    @Test
    void placingASecondBidAppendsANewCurrentBid() throws Exception {
        Auction auction = activeAuction("IB-C2", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "175.00"))
                .andExpect(status().is3xxRedirection());

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        var current = bidRepository.findCurrentBid(auction.auctionRef(), bidder.id()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(175.00).compareTo(current.amount()));

        var history = bidRepository.findByAuctionIdAndUserIdOrderByIdAsc(auction.auctionRef(), bidder.id());
        assertEquals(2, history.size());
        assertEquals(BidEventType.PLACED, history.get(0).eventType());
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(history.get(0).amount()));
        assertEquals(BidEventType.CHANGED, history.get(1).eventType());
        assertEquals(0, BigDecimal.valueOf(175.00).compareTo(history.get(1).amount()));
    }

    @Test
    void bidBelowStartPriceIsRejected() throws Exception {
        Auction auction = activeAuction("IB-C3", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "50.00"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void biddingOnADraftAuctionConflicts() throws Exception {
        Auction auction = draftAuction("IB-C4");

        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void biddingAfterTheAuctionHasEndedConflicts() throws Exception {
        Auction auction = activeAuction("IB-C5", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawingABidAppendsAWithdrawnRow() throws Exception {
        Auction auction = activeAuction("IB-D1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.auctionRef()));

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        Bid current = bidRepository.findCurrentBid(auction.auctionRef(), bidder.id()).orElseThrow();
        assertEquals(BidEventType.WITHDRAWN, current.eventType());
        assertTrue(bidRepository.findCurrentBid(auction.auctionRef(), bidder.id()).filter(Bid::isActive).isEmpty());
    }

    @Test
    void withdrawnBidNoLongerShowsAsMyCurrentBid() throws Exception {
        Auction auction = activeAuction("IB-D2", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You haven't placed a bid yet")));
    }

    @Test
    void withdrawingWithNoBidReturnsNotFound() throws Exception {
        Auction auction = activeAuction("IB-D3", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawingAfterTheAuctionHasEndedConflicts() throws Exception {
        Auction auction = activeAuction("IB-D4", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(2));
        mockMvc.perform(post("/auctions/{id}/bids", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());

        Auction ended = auctionRepository.save(new Auction(
                null, auction.auctionRef(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), auction.lifecycleStatus(), auction.startPrice(), auction.currentValue(),
                auction.currency(), auction.startsAt(), Instant.now().minusSeconds(1), auction.comment(),
                auction.serialNumber(), auction.createdAt()));

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", ended.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void detailShowsWinToTheWinnerWithTheWinningPrice() throws Exception {
        User winner = bidderUser();
        Auction auction = soldAuction("IB-R1", winner.id(), "300.00");

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You won this auction")))
                .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("300.00"),
                        org.hamcrest.Matchers.containsString("300,00"))));
    }

    @Test
    void detailShowsDidNotWinToANonWinnerWithoutRevealingThePrice() throws Exception {
        bidderUser();
        User otherWinner = userRepository.save(
                new User(null, "other-subject", "other@example.com", "Other", UserRole.USER, Instant.now()));
        Auction auction = soldAuction("IB-R2", otherWinner.id(), "300.00");

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You did not win")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("You won this auction"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("300.00"),
                        org.hamcrest.Matchers.containsString("300,00")))));
    }

    @Test
    void winnerPayingStampsPaidAtAndShowsPaymentReceived() throws Exception {
        User winner = bidderUser();
        Auction auction = soldAuction("IB-P1", winner.id(), "300.00");

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.auctionRef()));

        assertTrue(resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt() != null);

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment received")));
    }

    @Test
    void nonWinnerCannotPay() throws Exception {
        bidderUser();
        User otherWinner = userRepository.save(
                new User(null, "other-subject", "other@example.com", "Other", UserRole.USER, Instant.now()));
        Auction auction = soldAuction("IB-P2", otherWinner.id(), "300.00");

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());

        assertTrue(resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt() == null);
    }

    @Test
    void payingTwiceIsIdempotent() throws Exception {
        User winner = bidderUser();
        Auction auction = soldAuction("IB-P3", winner.id(), "300.00");

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());
        Instant firstPaidAt = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt();

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(firstPaidAt, resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt());
    }

    @Test
    void payingAnAuctionWithNoResultReturnsNotFound() throws Exception {
        Auction auction = activeAuction("IB-P4", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailShowsUnsoldWhenTheAuctionEndedWithNoSale() throws Exception {
        Auction auction = auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), "IB-R3", "Unsold auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.UNSOLD, BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60),
                null, null, Instant.now()));

        mockMvc.perform(get("/auctions/{id}", auction.auctionRef()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ended without a sale")));
    }

}
