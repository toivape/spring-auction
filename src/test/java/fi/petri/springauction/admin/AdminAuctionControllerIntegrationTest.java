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
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                "EUR", Instant.now(), null, null, null, Instant.now()));
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
    void adminCanLogInAndActivateADraftAuction() throws Exception {
        Auction auction = draftAuction();
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Auction reloaded = auctionRepository.findById(auction.id()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password("wrong-password"))
                .andExpect(unauthenticated());
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
        Auction auction = auctionRepository.save(new Auction(
                null, "IB-88888", "Already active", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now(), Instant.now().plusSeconds(3600), null, null, Instant.now()));
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/admin/auctions/{id}/activate", auction.id())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

}
