package fi.petri.springauction.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure recipient-set selection off the event payload — no Spring context. */
class AuctionFinalizedEventTest {

    private static AuctionFinalizedEvent event(Long winnerUserId, List<Long> bidderUserIds) {
        return new AuctionFinalizedEvent(1L, "Laptop", "EUR", winnerUserId, new BigDecimal("150"), bidderUserIds);
    }

    @Test
    void loserUserIdsExcludesTheWinnerAndKeepsEveryoneElse() {
        assertEquals(List.of(10L, 30L), event(20L, List.of(10L, 20L, 30L)).loserUserIds());
    }

    @Test
    void loserUserIdsIsEmptyForASoleBidder() {
        assertTrue(event(10L, List.of(10L)).loserUserIds().isEmpty());
    }
}
