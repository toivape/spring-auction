package fi.petri.springauction.auction;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidEventType;
import fi.petri.springauction.bid.BidRepository;
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

    private Auction activeAuction(String itemId, Instant startsAt, Instant endsAt) {
        return auctionRepository.save(new Auction(
                null, itemId, "Active auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                "EUR", startsAt, endsAt, null, null, Instant.now()));
    }

    private Auction draftAuction(String itemId) {
        return auctionRepository.save(new Auction(
                null, itemId, null, "Dell laptop", "laptops", null,
                AuctionLifecycleStatus.DRAFT, BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                "EUR", null, null, null, null, Instant.now()));
    }

    private RequestPostProcessor asBidder() {
        userRepository.findByGoogleSubjectId(SUBJECT).orElseGet(() -> userRepository.save(
                new User(null, SUBJECT, "bidder@example.com", "Bidder", UserRole.USER, Instant.now())));
        return SecurityMockMvcRequestPostProcessors.oidcLogin()
                .idToken(token -> token.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
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

        mockMvc.perform(post("/auctions/{id}/bids", bidOn.id())
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

        mockMvc.perform(get("/auctions/{id}", auction.id()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You haven't placed a bid yet")));
    }

    @Test
    void placingABidPersistsIt() throws Exception {
        Auction auction = activeAuction("IB-C1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.id()));

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        var bid = bidRepository.findCurrentBid(auction.id(), bidder.id()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(bid.amount()));
        assertEquals(BidEventType.PLACED, bid.eventType());
    }

    @Test
    void placingASecondBidAppendsANewCurrentBid() throws Exception {
        Auction auction = activeAuction("IB-C2", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "175.00"))
                .andExpect(status().is3xxRedirection());

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        var current = bidRepository.findCurrentBid(auction.id(), bidder.id()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(175.00).compareTo(current.amount()));

        var history = bidRepository.findByAuctionIdAndUserIdOrderByIdAsc(auction.id(), bidder.id());
        assertEquals(2, history.size());
        assertEquals(BidEventType.PLACED, history.get(0).eventType());
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(history.get(0).amount()));
        assertEquals(BidEventType.CHANGED, history.get(1).eventType());
        assertEquals(0, BigDecimal.valueOf(175.00).compareTo(history.get(1).amount()));
    }

    @Test
    void bidBelowStartPriceIsRejected() throws Exception {
        Auction auction = activeAuction("IB-C3", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "50.00"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void biddingOnADraftAuctionConflicts() throws Exception {
        Auction auction = draftAuction("IB-C4");

        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void biddingAfterTheAuctionHasEndedConflicts() throws Exception {
        Auction auction = activeAuction("IB-C5", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawingABidAppendsAWithdrawnRow() throws Exception {
        Auction auction = activeAuction("IB-D1", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.id()));

        User bidder = userRepository.findByGoogleSubjectId(SUBJECT).orElseThrow();
        Bid current = bidRepository.findCurrentBid(auction.id(), bidder.id()).orElseThrow();
        assertEquals(BidEventType.WITHDRAWN, current.eventType());
        assertTrue(bidRepository.findCurrentBid(auction.id(), bidder.id()).filter(Bid::isActive).isEmpty());
    }

    @Test
    void withdrawnBidNoLongerShowsAsMyCurrentBid() throws Exception {
        Auction auction = activeAuction("IB-D2", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/auctions/{id}", auction.id()).with(asBidder()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You haven't placed a bid yet")));
    }

    @Test
    void withdrawingWithNoBidReturnsNotFound() throws Exception {
        Auction auction = activeAuction("IB-D3", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawingAfterTheAuctionHasEndedConflicts() throws Exception {
        Auction auction = activeAuction("IB-D4", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(2));
        mockMvc.perform(post("/auctions/{id}/bids", auction.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "150.00"))
                .andExpect(status().is3xxRedirection());

        Auction ended = auctionRepository.save(new Auction(
                auction.id(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), auction.lifecycleStatus(), auction.startPrice(), auction.currentValue(),
                auction.currency(), auction.startsAt(), Instant.now().minusSeconds(1), auction.comment(),
                auction.serialNumber(), auction.createdAt()));

        mockMvc.perform(post("/auctions/{id}/bids/withdraw", ended.id())
                        .with(asBidder())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict());
    }

}
