package fi.petri.springauction.result;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.auction.AuctionType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentControllerIntegrationTest {

    private static final String WINNER_SUBJECT = "payment-test-winner";
    private static final String OTHER_SUBJECT = "payment-test-other";

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    AuctionResultRepository resultRepository;

    @Autowired
    UserRepository userRepository;

    private User winnerUser() {
        return userRepository.findByGoogleSubjectId(WINNER_SUBJECT).orElseGet(() -> userRepository.save(
                new User(null, WINNER_SUBJECT, "winner@example.com", "Winner", UserRole.USER, Instant.now())));
    }

    private User otherUser() {
        return userRepository.findByGoogleSubjectId(OTHER_SUBJECT).orElseGet(() -> userRepository.save(
                new User(null, OTHER_SUBJECT, "other@example.com", "Other", UserRole.USER, Instant.now())));
    }

    private RequestPostProcessor asWinner() {
        winnerUser();
        return SecurityMockMvcRequestPostProcessors.oidcLogin()
                .idToken(token -> token.subject(WINNER_SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor asOther() {
        otherUser();
        return SecurityMockMvcRequestPostProcessors.oidcLogin()
                .idToken(token -> token.subject(OTHER_SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Auction soldAuction(String itemId, Long winnerUserId, String price, ResultStatus resultStatus, Instant invalidatedAt) {
        Auction auction = auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Sold auction", "Dell laptop", "laptops",
                AuctionType.FIRST_PRICE, AuctionLifecycleStatus.SOLD, BigDecimal.valueOf(100), new BigDecimal(price),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60), null, null, Instant.now()));
        resultRepository.save(new AuctionResult(
                null, auction.auctionRef(), resultStatus, winnerUserId, new BigDecimal(price),
                Instant.now(), null, invalidatedAt, null, invalidatedAt != null ? "voided for test" : null, null));
        return auction;
    }

    @Test
    void winnerPayingStampsPaidAtAndRedirectsToDetail() throws Exception {
        User winner = winnerUser();
        Auction auction = soldAuction("IB-PAY1", winner.id(), "300.00", ResultStatus.SOLD, null);

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef()).with(asWinner())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auctions/" + auction.auctionRef()));

        AuctionResult result = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow();
        assertNotNull(result.paidAt());
    }

    @Test
    void payingTwiceIsASafeNoOp() throws Exception {
        User winner = winnerUser();
        Auction auction = soldAuction("IB-PAY2", winner.id(), "300.00", ResultStatus.SOLD, null);

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef()).with(asWinner())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());
        Instant firstPaidAt = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt();

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef()).with(asWinner())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(firstPaidAt, resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt());
    }

    @Test
    void nonWinnerCannotPay() throws Exception {
        User winner = winnerUser();
        otherUser();
        Auction auction = soldAuction("IB-PAY3", winner.id(), "300.00", ResultStatus.SOLD, null);

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef()).with(asOther())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());

        assertNull(resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().paidAt());
    }

    @Test
    void voidedResultIsNotPayable() throws Exception {
        User winner = winnerUser();
        Auction auction = soldAuction("IB-PAY4", winner.id(), "300.00", ResultStatus.VOIDED, Instant.now());

        mockMvc.perform(post("/auctions/{id}/pay", auction.auctionRef()).with(asWinner())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void payingForAuctionWithNoResultReturnsNotFound() throws Exception {
        mockMvc.perform(post("/auctions/{id}/pay", 999999L).with(asWinner())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousPayRedirectsToGoogleLogin() throws Exception {
        // The authorization filter redirects before the controller runs, so the row needn't exist.
        // Uses HttpClient (not MockMvc) because the mock servlet env doesn't reliably enforce the
        // catch-all oauth2Login redirect for the authenticated-only branch.
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auctions/123/pay"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(302, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.contains("/oauth2/authorization/google"));
    }
}
