package fi.petri.springauction.bid;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends Repository<Bid, BidId> {

    @Query("SELECT EXISTS (SELECT 1 FROM bid WHERE auction_id = :auctionId AND is_withdrawn = false)")
    boolean existsActiveBidForAuction(@Param("auctionId") Long auctionId);

}
