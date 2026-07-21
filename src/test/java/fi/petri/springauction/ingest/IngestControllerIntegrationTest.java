package fi.petri.springauction.ingest;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.security.ApiKeyAuthenticationFilter;
import fi.petri.springauction.security.IngestionSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestControllerIntegrationTest {

    private static final String NEW_AUCTION_ITEM_JSON = """
            {
              "id": "IB-12345",
              "startDate": "2026-07-13",
              "price": 1000,
              "currency": "EUR",
              "description": "Dell laptop, lightly used",
              "category": "laptops",
              "currentValue": 450.5,
              "comment": "small scratch on lid",
              "serialNumber": "SN-998877"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    IngestionSecurityProperties ingestionSecurityProperties;

    @Test
    void postingANewAuctionItemPersistsItAsADraftAuction() throws Exception {
        mockMvc.perform(post("/api/ingest")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, ingestionSecurityProperties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_AUCTION_ITEM_JSON))
                .andExpect(status().isCreated());

        List<Auction> auctions = auctionRepository.findAll();
        Optional<Auction> match = auctions.stream().filter(a -> "IB-12345".equals(a.itemId())).findFirst();
        assertTrue(match.isPresent());
        Auction saved = match.get();
        assertEquals("IB-12345", saved.itemId());
        assertEquals("Dell laptop, lightly used", saved.description());
        assertEquals("laptops", saved.category());
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(saved.startPrice()));
        assertEquals(0, BigDecimal.valueOf(450.5).compareTo(saved.currentValue()));
        assertEquals("EUR", saved.currency());
        assertEquals("small scratch on lid", saved.comment());
        assertEquals("SN-998877", saved.serialNumber());
        assertEquals(AuctionLifecycleStatus.DRAFT, saved.lifecycleStatus());
        assertNull(saved.title());
        assertEquals(AuctionType.FIRST_PRICE, saved.auctionType());
        assertNull(saved.endsAt());
    }

    @Test
    void postingWithoutAValidApiKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/ingest")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_AUCTION_ITEM_JSON))
                .andExpect(status().isForbidden());

        assertFalse(auctionRepository.findAll().stream().anyMatch(a -> "IB-12345".equals(a.itemId())));
    }

    @Test
    void postingAnInvalidAuctionItemReturnsAProblemDetail() throws Exception {
        mockMvc.perform(post("/api/ingest")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, ingestionSecurityProperties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

}
