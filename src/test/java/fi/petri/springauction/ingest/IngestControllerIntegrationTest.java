package fi.petri.springauction.ingest;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isOk());

        List<Auction> auctions = auctionRepository.findAll();
        assertEquals(1, auctions.size());
        Auction saved = auctions.get(0);
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
        assertNull(saved.auctionType());
        assertNull(saved.endsAt());
    }

    @Test
    void postingWithoutAValidApiKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/ingest")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_AUCTION_ITEM_JSON))
                .andExpect(status().isForbidden());

        assertEquals(0, auctionRepository.findAll().size());
    }

}
