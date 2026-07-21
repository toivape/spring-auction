package fi.petri.springauction.auction;

/**
 * How the winning price is settled. FIRST_PRICE: the winner pays their own bid. SECOND_PRICE (Vickrey):
 * the winner pays the second-highest bid (or the start price if they were the only bidder). Stored by
 * name in the {@code auction_type} TEXT column, same as {@link AuctionLifecycleStatus}.
 */
public enum AuctionType {
    FIRST_PRICE("First-price"),
    SECOND_PRICE("Second-price (sealed)");

    private final String displayName;

    AuctionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
