package fi.petri.springauction.admin;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.security.AdminBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAuctionControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    AdminBootstrapProperties adminBootstrapProperties;

    private Auction draftAuction() {
        return auctionRepository.save(new Auction(
                null, "IB-99999", null, "Dell laptop", "laptops", null,
                AuctionLifecycleStatus.DRAFT, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", null, null, null, null, Instant.now()));
    }

    private Auction activeAuction(String itemId, Instant endsAt) {
        return auctionRepository.save(new Auction(
                null, itemId, "Active auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now().minusSeconds(3600), endsAt, null, null, Instant.now()));
    }

    private Auction unsoldAuction(String itemId) {
        return auctionRepository.save(new Auction(
                null, itemId, "Unsold auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.UNSOLD, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), null, null, Instant.now()));
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password(adminBootstrapProperties.password()))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void adminCanListAuctions() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void listingAuctionsWithoutLoggingInIsRejected() throws Exception {
        mockMvc.perform(get("/admin/auctions"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminCanViewTheActivateForm() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions/{id}/activate", auction.id()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void activatingWithoutDatesDefaultsToNowAndThirtyDays() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        Instant before = Instant.now();
        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));
        Instant after = Instant.now();

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertFalse(reloaded.startsAt().isBefore(before.minusSeconds(1)));
        assertFalse(reloaded.startsAt().isAfter(after.plusSeconds(1)));
        assertEquals(reloaded.startsAt().plus(Duration.ofDays(30)), reloaded.endsAt());
    }

    @Test
    void activatingWithExplicitDatesUsesThem() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .session(session)
                        .with(csrf())
                        .param("startsAt", "2026-08-01T10:00")
                        .param("endsAt", "2026-09-01T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), reloaded.startsAt());
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), reloaded.endsAt());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password("wrong-password"))
                .andExpect(unauthenticated());
    }

    @Test
    void loggingInRedirectsToTheAuctionsList() throws Exception {
        mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password(adminBootstrapProperties.password()))
                .andExpect(redirectedUrl("/admin/auctions"));
    }

    @Test
    void adminCanLogOut() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?logout"));

        mockMvc.perform(get("/admin/auctions").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void activatingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = draftAuction();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.DRAFT, reloaded.lifecycleStatus());
    }

    @Test
    void activatingAnAlreadyActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-88888", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanArchiveAnUnsoldAuction() throws Exception {
        Auction auction = unsoldAuction("IB-77777");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ARCHIVED, reloaded.lifecycleStatus());
    }

    @Test
    void archivingAnActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-66666", Instant.now().minusSeconds(60));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingADraftAuctionConflicts() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = unsoldAuction("IB-44444");

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.id())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
    }

    @Test
    void adminCanViewTheExtendForm() throws Exception {
        Auction auction = unsoldAuction("IB-33333");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions/{id}/extend", auction.id()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void extendingAnUnsoldAuctionReactivatesItWithNewEndDate() throws Exception {
        Auction auction = unsoldAuction("IB-22222");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.id())
                        .session(session)
                        .with(csrf())
                        .param("endsAt", "2026-09-01T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(auction.startsAt(), reloaded.startsAt());
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), reloaded.endsAt());
    }

    @Test
    void extendingAnUnsoldAuctionWithoutEndDateDefaultsToThirtyDaysFromNow() throws Exception {
        Auction auction = unsoldAuction("IB-11111");
        MockHttpSession session = loginAsAdmin();

        Instant before = Instant.now();
        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));
        Instant after = Instant.now();

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertFalse(reloaded.endsAt().isBefore(before.plus(Duration.ofDays(30)).minusSeconds(1)));
        assertFalse(reloaded.endsAt().isAfter(after.plus(Duration.ofDays(30)).plusSeconds(1)));
    }

    @Test
    void extendingAnActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-10101", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void extendingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = unsoldAuction("IB-20202");

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.id())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
    }

}
