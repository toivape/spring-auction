package fi.petri.springauction.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything the My Account page shows for the current user, split by outcome. Winning prices of
 * auctions the user did not win are never included — bids are sealed.
 */
public record AccountView(
        List<OngoingEntry> ongoing,
        List<WonEntry> won,
        List<LostEntry> lost) {

    /** An auction still ACTIVE where the user has a live bid. */
    public record OngoingEntry(Long auctionRef, String title, String itemId, BigDecimal myBid,
                               String currency, Instant endsAt) {
    }

    /** An auction the user won; {@code paid} reflects whether payment has been recorded. */
    public record WonEntry(Long auctionRef, String title, String itemId, BigDecimal winningPrice,
                           String currency, boolean paid) {
    }

    /** An auction the user bid on that sold to someone else (winning price withheld — sealed). */
    public record LostEntry(Long auctionRef, String title, String itemId, String currency) {
    }
}
