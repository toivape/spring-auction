package fi.petri.springauction.bid;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("bid")
public record Bid(
        @Id @Embedded.Empty BidId id,
        BigDecimal amount,
        boolean isWithdrawn,
        Long moderatedBy,
        String moderationReason,
        Instant createdAt,
        Instant updatedAt
) {
}
