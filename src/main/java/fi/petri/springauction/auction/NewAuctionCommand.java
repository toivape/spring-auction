package fi.petri.springauction.auction;

import java.math.BigDecimal;

/**
 * Validated input for creating a new DRAFT auction (see {@link AuctionService#create}). Optional fields
 * ({@code title}, {@code currentValue}, {@code currency}, {@code comment}, {@code serialNumber}) may be
 * null; the service applies defaults.
 */
public record NewAuctionCommand(
        String itemId,
        String title,
        String description,
        String category,
        AuctionType auctionType,
        BigDecimal startPrice,
        BigDecimal currentValue,
        String currency,
        String comment,
        String serialNumber
) {
}
