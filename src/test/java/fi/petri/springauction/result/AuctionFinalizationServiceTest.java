package fi.petri.springauction.result;

import fi.petri.springauction.auction.AuctionType;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidEventType;
import fi.petri.springauction.result.AuctionFinalizationService.Outcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure winner/price rules — no Spring context, no DB. */
class AuctionFinalizationServiceTest {

    private static Bid bid(long id, long userId, String amount) {
        return new Bid(id, 1L, userId, BidEventType.PLACED, new BigDecimal(amount), userId, null, Instant.EPOCH);
    }

    @Test
    void firstPriceWinnerPaysOwnAmount() {
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(1, 10, "100"), bid(2, 20, "150"), bid(3, 30, "120")),
                AuctionType.FIRST_PRICE, new BigDecimal("50"));

        assertEquals(20L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("150").compareTo(outcome.price()));
    }

    @Test
    void secondPriceWinnerPaysSecondHighestAmount() {
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(1, 10, "100"), bid(2, 20, "150"), bid(3, 30, "120")),
                AuctionType.SECOND_PRICE, new BigDecimal("50"));

        assertEquals(20L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("120").compareTo(outcome.price()));
    }

    @Test
    void secondPriceSingleBidderPaysStartPrice() {
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(1, 10, "300")),
                AuctionType.SECOND_PRICE, new BigDecimal("50"));

        assertEquals(10L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("50").compareTo(outcome.price()));
    }

    @Test
    void topAmountTieIsBrokenByLowestBidId() {
        // Two bidders tie at 200; the lower bid id (recorded first) wins.
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(7, 30, "200"), bid(4, 10, "200"), bid(5, 20, "150")),
                AuctionType.FIRST_PRICE, new BigDecimal("50"));

        assertEquals(10L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("200").compareTo(outcome.price()));
    }

    @Test
    void secondPriceTopAmountTieMeansWinnerPaysThatSameAmount() {
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(4, 10, "200"), bid(7, 30, "200")),
                AuctionType.SECOND_PRICE, new BigDecimal("50"));

        assertEquals(10L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("200").compareTo(outcome.price()));
    }

    @Test
    void nullAuctionTypeIsTreatedAsFirstPrice() {
        Outcome outcome = AuctionFinalizationService.computeOutcome(
                List.of(bid(1, 10, "100"), bid(2, 20, "150")),
                null, new BigDecimal("50"));

        assertEquals(20L, outcome.winnerUserId());
        assertEquals(0, new BigDecimal("150").compareTo(outcome.price()));
    }

}
