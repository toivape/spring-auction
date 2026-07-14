package fi.petri.springauction.bid;

import java.io.Serializable;

public record BidId(Long auctionId, Long userId) implements Serializable {
}
