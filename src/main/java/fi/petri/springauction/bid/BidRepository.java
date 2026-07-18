package fi.petri.springauction.bid;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends ListCrudRepository<Bid, Long> {

    /** True if any bidder's latest row on this auction is still active (PLACED/CHANGED). */
    @Query("""
            SELECT EXISTS (
                SELECT 1 FROM bid b
                WHERE b.auction_id = :auctionId
                  AND b.id = (SELECT MAX(b2.id) FROM bid b2
                              WHERE b2.auction_id = b.auction_id AND b2.user_id = b.user_id)
                  AND b.event_type IN ('PLACED', 'CHANGED')
            )
            """)
    boolean existsActiveBidForAuction(@Param("auctionId") Long auctionId);

    /** A user's current (latest) row for an auction, whatever its event type. */
    @Query("SELECT * FROM bid WHERE auction_id = :auctionId AND user_id = :userId ORDER BY id DESC LIMIT 1")
    Optional<Bid> findCurrentBid(@Param("auctionId") Long auctionId, @Param("userId") Long userId);

    /** For each auction, this user's current bid if it's still active (latest row, PLACED/CHANGED only). */
    @Query("""
            SELECT * FROM bid b
            WHERE b.user_id = :userId
              AND b.id = (SELECT MAX(b2.id) FROM bid b2
                          WHERE b2.auction_id = b.auction_id AND b2.user_id = b.user_id)
              AND b.event_type IN ('PLACED', 'CHANGED')
            """)
    List<Bid> findCurrentActiveBidsForUser(@Param("userId") Long userId);

    /** Eligible bids for finalization: each user's latest row on this auction, if still active (PLACED/CHANGED). */
    @Query("""
            SELECT * FROM bid b
            WHERE b.auction_id = :auctionId
              AND b.id = (SELECT MAX(b2.id) FROM bid b2
                          WHERE b2.auction_id = b.auction_id AND b2.user_id = b.user_id)
              AND b.event_type IN ('PLACED', 'CHANGED')
            """)
    List<Bid> findEligibleBids(@Param("auctionId") Long auctionId);

    /** Full append-only history for one bidder on one auction, oldest first. */
    List<Bid> findByAuctionIdAndUserIdOrderByIdAsc(Long auctionId, Long userId);

}
