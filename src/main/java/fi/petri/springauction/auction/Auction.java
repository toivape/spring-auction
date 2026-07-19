package fi.petri.springauction.auction;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("auction")
public record Auction(
        @Id Long id,
        Long auctionRef,
        String itemId,
        String title,
        String description,
        String category,
        String auctionType,
        AuctionLifecycleStatus lifecycleStatus,
        BigDecimal startPrice,
        BigDecimal currentValue,
        String currency,
        Instant startsAt,
        Instant endsAt,
        String comment,
        String serialNumber,
        Instant createdAt
) {
}
