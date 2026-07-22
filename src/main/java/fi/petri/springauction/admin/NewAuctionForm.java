package fi.petri.springauction.admin;

import fi.petri.springauction.auction.AuctionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Backing object for the admin "new auction" form. Field-level validation lives here (Bean Validation);
 * the duplicate-item-id check and defaulting live in {@link fi.petri.springauction.auction.AuctionService}.
 * Empty numeric inputs are bound as {@code null} (see the controller's {@code @InitBinder}).
 */
public record NewAuctionForm(
        @NotBlank String itemId,
        String title,
        @NotBlank String description,
        @NotBlank String category,
        AuctionType auctionType,
        @NotNull @Positive BigDecimal startPrice,
        @PositiveOrZero BigDecimal currentValue,
        String currency,
        String comment,
        String serialNumber
) {
    /** An empty form pre-populated with sensible defaults, for the initial GET render. */
    public static NewAuctionForm empty() {
        return new NewAuctionForm(null, null, null, null, AuctionType.FIRST_PRICE, null, null, "EUR", null, null);
    }
}
