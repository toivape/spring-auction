package fi.petri.springauction.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AuctionRequest(
        @NotBlank String id,
        @NotBlank String startDate,
        @Positive Double price,
        @NotBlank String currency,
        @NotBlank String description,
        @NotBlank String category,
        @PositiveOrZero Double currentValue,
        String comment,
        String serialNumber
) {
}
