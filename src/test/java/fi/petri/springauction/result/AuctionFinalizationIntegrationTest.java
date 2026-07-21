package fi.petri.springauction.result;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidEventType;
import fi.petri.springauction.bid.BidRepository;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuctionFinalizationIntegrationTest {

    @Autowired
    AuctionFinalizationService finalizationService;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    AuctionResultRepository resultRepository;

    @Autowired
    UserRepository userRepository;

    private int userSeq = 0;

    private Auction endedAuction(String itemId, AuctionType auctionType, String startPrice) {
        return auctionRepository.save(new Auction(
                null, auctionRepository.nextAuctionRef(), itemId, "Ended auction", "Dell laptop", "laptops", auctionType,
                AuctionLifecycleStatus.ACTIVE, new BigDecimal(startPrice), new BigDecimal(startPrice),
                "EUR", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60),
                null, null, Instant.now()));
    }

    private User user() {
        return userRepository.save(new User(
                null, null, "bidder" + (++userSeq) + "@example.com", "Bidder", UserRole.USER, Instant.now()));
    }

    private void placeBid(Long auctionId, Long userId, String amount) {
        bidRepository.save(new Bid(null, auctionId, userId, BidEventType.PLACED,
                new BigDecimal(amount), userId, null, Instant.now()));
    }

    private void withdraw(Long auctionId, Long userId, String priorAmount) {
        bidRepository.save(new Bid(null, auctionId, userId, BidEventType.WITHDRAWN,
                new BigDecimal(priorAmount), userId, null, Instant.now()));
    }

    @Test
    void firstPriceAuctionSellsToHighestBidderAtTheirOwnAmount() {
        Auction auction = endedAuction("FIN-1", AuctionType.FIRST_PRICE, "100");
        User a = user();
        User b = user();
        placeBid(auction.auctionRef(), a.id(), "150");
        placeBid(auction.auctionRef(), b.id(), "220");

        finalizationService.finalizeAuction(auction.auctionRef());

        AuctionResult result = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow();
        assertEquals(ResultStatus.SOLD, result.resultStatus());
        assertEquals(b.id(), result.winnerUserId());
        assertEquals(0, new BigDecimal("220").compareTo(result.winningPrice()));
        assertEquals(AuctionLifecycleStatus.SOLD,
                auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow().lifecycleStatus());
    }

    @Test
    void secondPriceAuctionChargesTheWinnerTheSecondHighestAmount() {
        Auction auction = endedAuction("FIN-2", AuctionType.SECOND_PRICE, "100");
        User a = user();
        User b = user();
        placeBid(auction.auctionRef(), a.id(), "150");
        placeBid(auction.auctionRef(), b.id(), "220");

        finalizationService.finalizeAuction(auction.auctionRef());

        AuctionResult result = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow();
        assertEquals(b.id(), result.winnerUserId());
        assertEquals(0, new BigDecimal("150").compareTo(result.winningPrice()));
    }

    @Test
    void onlyTheLatestBidPerUserCounts() {
        Auction auction = endedAuction("FIN-3", AuctionType.FIRST_PRICE, "100");
        User a = user();
        User b = user();
        placeBid(auction.auctionRef(), a.id(), "150");
        placeBid(auction.auctionRef(), a.id(), "300"); // a raises to 300 (new row) — this is what counts
        placeBid(auction.auctionRef(), b.id(), "220");

        finalizationService.finalizeAuction(auction.auctionRef());

        AuctionResult result = resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow();
        assertEquals(a.id(), result.winnerUserId());
        assertEquals(0, new BigDecimal("300").compareTo(result.winningPrice()));
    }

    @Test
    void auctionWithOnlyWithdrawnBidsIsLeftForTheUnsoldJob() {
        Auction auction = endedAuction("FIN-4", AuctionType.FIRST_PRICE, "100");
        User a = user();
        placeBid(auction.auctionRef(), a.id(), "150");
        withdraw(auction.auctionRef(), a.id(), "150");

        finalizationService.finalizeAuction(auction.auctionRef());

        assertTrue(resultRepository.findByAuctionId(auction.auctionRef()).isEmpty());
        assertEquals(AuctionLifecycleStatus.ACTIVE,
                auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow().lifecycleStatus());
    }

    @Test
    void finalizingTwiceWritesOneResultAndIsANoOpTheSecondTime() {
        Auction auction = endedAuction("FIN-5", AuctionType.FIRST_PRICE, "100");
        User a = user();
        placeBid(auction.auctionRef(), a.id(), "150");

        finalizationService.finalizeAuction(auction.auctionRef());
        finalizationService.finalizeAuction(auction.auctionRef());

        long results = resultRepository.findAll().stream()
                .filter(r -> r.auctionId().equals(auction.auctionRef())).count();
        assertEquals(1, results);
        assertEquals(AuctionLifecycleStatus.SOLD,
                auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow().lifecycleStatus());
    }

    @Test
    void batchFinalizesEveryEndedAuctionThatHasBids() {
        Auction auction = endedAuction("FIN-6", AuctionType.SECOND_PRICE, "100");
        User a = user();
        User b = user();
        placeBid(auction.auctionRef(), a.id(), "150");
        placeBid(auction.auctionRef(), b.id(), "180");

        finalizationService.finalizeEndedAuctions();

        assertEquals(AuctionLifecycleStatus.SOLD,
                auctionRepository.findCurrentByRef(auction.auctionRef()).orElseThrow().lifecycleStatus());
        assertEquals(0, new BigDecimal("150").compareTo(
                resultRepository.findByAuctionId(auction.auctionRef()).orElseThrow().winningPrice()));
    }

}
