package fi.petri.springauction.admin;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.security.AdminBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

    @Autowired
    JdbcClient jdbcClient;

    private Auction draftAuction() {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), "IB-99999", null, "Dell laptop", "laptops", AuctionType.FIRST_PRICE,
                AuctionLifecycleStatus.DRAFT, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", null, null, null, null, Instant.now()));
    }

    /** Same zone-aware conversion the controller does for datetime-local form input. */
    private static Instant localInstant(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant();
    }

    private Auction activeAuction(String itemId, Instant endsAt) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Active auction", "Dell laptop", "laptops", AuctionType.FIRST_PRICE,
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now().minusSeconds(3600), endsAt, null, null, Instant.now()));
    }

    private Auction unsoldAuction(String itemId) {
        // Microsecond precision: Postgres timestamptz rounds sub-micro nanos, so an exact round-trip
        // comparison only holds if the in-memory value has no nanos beyond micros to begin with.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Unsold auction", "Dell laptop", "laptops", AuctionType.FIRST_PRICE,
                AuctionLifecycleStatus.UNSOLD, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", now.minusSeconds(7200), now.minusSeconds(3600), null, null, now));
    }

    private Auction cancelledAuction(String itemId) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Cancelled auction", "Dell laptop", "laptops", AuctionType.FIRST_PRICE,
                AuctionLifecycleStatus.CANCELLED, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().plusSeconds(3600), null, null, Instant.now()));
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
        // Filtered to CANCELLED (no seed data uses this status) so the assertion doesn't depend on
        // how many DRAFT/ACTIVE/UNSOLD rows the migration seeds — those grow independently over time.
        Auction auction = cancelledAuction("IB-90909");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions").session(session).param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void filteringAuctionsByStatusShowsOnlyThatStatus() throws Exception {
        Auction draft = draftAuction();
        Auction active = activeAuction("IB-70707", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        // size=60 gives headroom over the growing TEST-ACTIVE-* seed rows so this doesn't get
        // page-boundary-flaky the way adminCanListAuctions etc. did after seed data grew past 15.
        mockMvc.perform(get("/admin/auctions").session(session).param("status", "ACTIVE").param("size", "60"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(active.itemId())))
                .andExpect(content().string(not(containsString(draft.itemId()))));
    }

    @Test
    void invalidPageSizeFallsBackToDefault() throws Exception {
        // Filtered to CANCELLED for the same seed-data-independence reason as adminCanListAuctions above.
        Auction auction = cancelledAuction("IB-91919");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions").session(session).param("status", "CANCELLED").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void pagingReturnsRequestedPage() throws Exception {
        // CANCELLED has no seed rows, so page boundaries here are independent of migration seed data.
        Auction firstAuction = null;
        Auction lastAuction = null;
        for (int i = 0; i < 16; i++) {
            Auction auction = auctionRepository.save(new Auction(
                    null, auctionRepository.nextAuctionRef(), "IB-PAGE-" + i, null, "Dell laptop", "laptops", AuctionType.FIRST_PRICE,
                    AuctionLifecycleStatus.CANCELLED, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                    "EUR", null, null, null, null, Instant.now()));
            if (i == 0) {
                firstAuction = auction;
            }
            lastAuction = auction;
        }
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions").session(session).param("status", "CANCELLED").param("size", "15").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(firstAuction.itemId())))
                .andExpect(content().string(not(containsString(lastAuction.itemId()))));

        mockMvc.perform(get("/admin/auctions").session(session).param("status", "CANCELLED").param("size", "15").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(lastAuction.itemId())))
                .andExpect(content().string(not(containsString(firstAuction.itemId()))));
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

        mockMvc.perform(get("/admin/auctions/{id}/activate", auction.auctionRef()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void activatingWithoutDatesDefaultsToNowAndThirtyDays() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        Instant before = Instant.now();
        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));
        Instant after = Instant.now();

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertFalse(reloaded.startsAt().isBefore(before.minusSeconds(1)));
        assertFalse(reloaded.startsAt().isAfter(after.plusSeconds(1)));
        assertEquals(reloaded.startsAt().plus(Duration.ofDays(30)), reloaded.endsAt());
    }

    @Test
    void activatingWithExplicitDatesUsesThem() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .session(session)
                        .with(csrf())
                        .param("startsAt", "2026-08-01T10:00")
                        .param("endsAt", "2026-09-01T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(localInstant("2026-08-01T10:00"), reloaded.startsAt());
        assertEquals(localInstant("2026-09-01T10:00"), reloaded.endsAt());
    }

    @Test
    void activatingWithSecondPricePersistsTheChosenType() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .session(session)
                        .with(csrf())
                        .param("auctionType", "SECOND_PRICE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionType.SECOND_PRICE, reloaded.auctionType());
    }

    @Test
    void activatingWithoutATypeDefaultsToFirstPrice() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionType.FIRST_PRICE, reloaded.auctionType());
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

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.DRAFT, reloaded.lifecycleStatus());
    }

    @Test
    void activatingAnAlreadyActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-88888", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanArchiveAnUnsoldAuction() throws Exception {
        Auction auction = unsoldAuction("IB-77777");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ARCHIVED, reloaded.lifecycleStatus());
    }

    @Test
    void archivingAnActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-66666", Instant.now().minusSeconds(60));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingADraftAuctionConflicts() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = unsoldAuction("IB-44444");

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.auctionRef())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
    }

    @Test
    void adminCanViewTheExtendForm() throws Exception {
        Auction auction = unsoldAuction("IB-33333");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin/auctions/{id}/extend", auction.auctionRef()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(auction.itemId())));
    }

    @Test
    void extendingAnUnsoldAuctionReactivatesItWithNewEndDate() throws Exception {
        Auction auction = unsoldAuction("IB-22222");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf())
                        .param("endsAt", "2026-09-01T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(auction.startsAt(), reloaded.startsAt());
        assertEquals(localInstant("2026-09-01T10:00"), reloaded.endsAt());
    }

    @Test
    void extendingAnUnsoldAuctionWithoutEndDateDefaultsToThirtyDaysFromNow() throws Exception {
        Auction auction = unsoldAuction("IB-11111");
        MockHttpSession session = loginAsAdmin();

        Instant before = Instant.now();
        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));
        Instant after = Instant.now();

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertFalse(reloaded.endsAt().isBefore(before.plus(Duration.ofDays(30)).minusSeconds(1)));
        assertFalse(reloaded.endsAt().isAfter(after.plus(Duration.ofDays(30)).plusSeconds(1)));
    }

    @Test
    void extendingAnActiveAuctionConflicts() throws Exception {
        Auction auction = activeAuction("IB-10101", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void extendingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = unsoldAuction("IB-20202");

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
    }

    @Test
    void adminCanCancelADraftAuction() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/cancel", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.CANCELLED, reloaded.lifecycleStatus());
    }

    @Test
    void adminCanCancelAnActiveAuction() throws Exception {
        Auction auction = activeAuction("IB-30303", Instant.now().plusSeconds(3600));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/cancel", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.CANCELLED, reloaded.lifecycleStatus());
    }

    @Test
    void cancellingAnUnsoldAuctionConflicts() throws Exception {
        Auction auction = unsoldAuction("IB-40404");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/cancel", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingAnAlreadyCancelledAuctionConflicts() throws Exception {
        Auction auction = cancelledAuction("IB-50505");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/cancel", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingWithoutLoggingInIsRejected() throws Exception {
        Auction auction = draftAuction();

        mockMvc.perform(post("/admin/auctions/{id}/cancel", auction.auctionRef())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.DRAFT, reloaded.lifecycleStatus());
    }

    @Test
    void adminCanArchiveACancelledAuction() throws Exception {
        Auction auction = cancelledAuction("IB-60606");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/archive", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ARCHIVED, reloaded.lifecycleStatus());
    }

    @Test
    void extendingAnUnsoldAuctionWithANewStartPriceUpdatesIt() throws Exception {
        Auction auction = unsoldAuction("IB-55511");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf())
                        .param("endsAt", "2026-09-01T10:00")
                        .param("startPrice", "1500.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(0, new BigDecimal("1500.00").compareTo(reloaded.startPrice()));
        // No active bids on an UNSOLD auction, so current value tracks the new start price.
        assertEquals(0, new BigDecimal("1500.00").compareTo(reloaded.currentValue()));
    }

    @Test
    void extendingAnUnsoldAuctionWithoutAStartPriceKeepsTheOriginal() throws Exception {
        Auction auction = unsoldAuction("IB-55522");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auctions"));

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
        assertEquals(0, auction.startPrice().compareTo(reloaded.startPrice()));
    }

    @Test
    void extendingAnUnsoldAuctionWithANegativeStartPriceIsRejected() throws Exception {
        Auction auction = unsoldAuction("IB-55544");
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", auction.auctionRef())
                        .session(session)
                        .with(csrf())
                        .param("startPrice", "-1.00"))
                .andExpect(status().isUnprocessableEntity());

        // Rejected before any version is appended: the auction stays UNSOLD at its original price.
        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
        assertEquals(0, auction.startPrice().compareTo(reloaded.startPrice()));
    }

    @Test
    void aTransitionAppendsANewVersionAndLeavesTheOldRowIntact() throws Exception {
        Auction original = unsoldAuction("IB-55533");
        Long ref = original.auctionRef();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/extend", ref)
                        .session(session)
                        .with(csrf())
                        .param("startPrice", "1500.00"))
                .andExpect(status().is3xxRedirection());

        // Append-only: extend INSERTs a new version row rather than mutating the UNSOLD one.
        long versions = jdbcClient.sql("SELECT count(*) FROM auction WHERE auction_ref = :ref")
                .param("ref", ref).query(Long.class).single();
        assertEquals(2, versions);

        // The original version row is untouched.
        Auction oldRow = auctionRepository.findById(original.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, oldRow.lifecycleStatus());
        assertEquals(0, new BigDecimal("1000.00").compareTo(oldRow.startPrice()));

        // The current view is the newest version.
        Auction current = auctionRepository.findCurrentByRef(ref).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, current.lifecycleStatus());
        assertEquals(0, new BigDecimal("1500.00").compareTo(current.startPrice()));
    }

}
