package fi.petri.springauction.notification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published (buffered until commit) by {@code AuctionFinalizationService} when an auction is finalized
 * as SOLD. Carries everything the notification listener needs to pick recipients and render emails, so
 * the listener never has to re-read the result/bid tables.
 */
public record AuctionFinalizedEvent(
        Long auctionId,
        String auctionTitle,
        String currency,
        Long winnerUserId,
        BigDecimal winningPrice,
        List<Long> bidderUserIds) {

    /** Bidders who did not win — one "lose" email each. Pure, so it's unit-testable off the payload. */
    public List<Long> loserUserIds() {
        return bidderUserIds.stream().filter(id -> !id.equals(winnerUserId)).toList();
    }
}
