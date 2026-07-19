package fi.petri.springauction.auction;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuctionServiceIntegrationTest {

    @Autowired
    AuctionService auctionService;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcClient jdbcClient;

    private Auction activeAuction(String itemId, Instant endsAt) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Active auction", "Dell laptop", "laptops", "FIRST_PRICE",
                AuctionLifecycleStatus.ACTIVE, BigDecimal.valueOf(1000), BigDecimal.valueOf(450),
                "EUR", Instant.now().minusSeconds(7200), endsAt, null, null, Instant.now()));
    }

    private void placeBidOn(Long auctionId) {
        User bidder = userRepository.save(new User(null, null, "bidder@example.com", "Bidder", UserRole.USER, Instant.now()));
        jdbcClient.sql("INSERT INTO bid (auction_id, user_id, event_type, amount, actor_user_id) VALUES (:auctionId, :userId, 'PLACED', :amount, :userId)")
                .param("auctionId", auctionId)
                .param("userId", bidder.id())
                .param("amount", BigDecimal.valueOf(500))
                .update();
    }

    @Test
    void anEndedAuctionWithNoBidsBecomesUnsold() {
        Auction auction = activeAuction("IB-99001", Instant.now().minusSeconds(60));

        auctionService.finalizeUnsold();

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.UNSOLD, reloaded.lifecycleStatus());
    }

    @Test
    void anEndedAuctionWithAnActiveBidStaysActive() {
        Auction auction = activeAuction("IB-99002", Instant.now().minusSeconds(60));
        placeBidOn(auction.auctionRef());

        auctionService.finalizeUnsold();

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
    }

    @Test
    void anAuctionNotYetEndedStaysActive() {
        Auction auction = activeAuction("IB-99003", Instant.now().plusSeconds(3600));

        auctionService.finalizeUnsold();

        Auction reloaded = auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow();
        assertEquals(AuctionLifecycleStatus.ACTIVE, reloaded.lifecycleStatus());
    }

}
