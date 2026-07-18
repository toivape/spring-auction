package fi.petri.springauction.result;

import java.math.BigDecimal;

/**
 * What the current user should see about a finalized auction on the detail page. For a non-winner
 * {@code winningPrice} is {@code null} — a sealed auction never reveals the winning amount to losers.
 */
public record AuctionOutcomeView(
        ResultStatus status,
        boolean currentUserWon,
        BigDecimal winningPrice,
        boolean paid) {
}
