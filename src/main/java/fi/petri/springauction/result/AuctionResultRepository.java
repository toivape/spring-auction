package fi.petri.springauction.result;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionResultRepository extends ListCrudRepository<AuctionResult, Long> {

    boolean existsByAuctionId(Long auctionId);

    Optional<AuctionResult> findByAuctionId(Long auctionId);

    /** Results this user won. */
    List<AuctionResult> findByWinnerUserId(Long winnerUserId);

    /** SOLD results (not voided) on auctions this user bid on but did not win. */
    @Query("""
            SELECT r.* FROM auction_result r
            WHERE r.result_status = 'SOLD'
              AND r.invalidated_at IS NULL
              AND (r.winner_user_id IS NULL OR r.winner_user_id <> :userId)
              AND r.auction_id IN (SELECT DISTINCT b.auction_id FROM bid b WHERE b.user_id = :userId)
            """)
    List<AuctionResult> findLostResultsForUser(@Param("userId") Long userId);

}
