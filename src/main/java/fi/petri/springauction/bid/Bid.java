package fi.petri.springauction.bid;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only bid event. Place/update/withdraw/moderate each INSERT a new row; rows are never mutated.
 * A user's current bid for an auction is their latest ({@code MAX(id)}) row — active only if that row is
 * {@link BidEventType#PLACED} or {@link BidEventType#CHANGED}.
 */
@Table("bid")
public record Bid(
        @Id Long id,
        Long auctionId,
        Long userId,
        BidEventType eventType,
        BigDecimal amount,
        Long actorUserId,
        String reason,
        Instant createdAt
) {

    public boolean isActive() {
        return eventType == BidEventType.PLACED || eventType == BidEventType.CHANGED;
    }
}
