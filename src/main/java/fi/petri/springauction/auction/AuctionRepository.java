package fi.petri.springauction.auction;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The {@code auction} table is append-only: every change INSERTs a new version row (see the migration).
 * The current state of a logical auction is its latest row (MAX(id)) for a given {@code auction_ref}.
 * Reads below collapse to that current row; writes go through {@code save(new Auction(null, ...))}
 * (null @Id ⇒ INSERT), never an in-place update. {@code lifecycle_status} is bound as a String name
 * so no enum-parameter conversion is relied upon; a null status matches every current auction.
 */
public interface AuctionRepository extends ListCrudRepository<Auction, Long> {

    @Query("SELECT * FROM auction WHERE auction_ref = :ref ORDER BY id DESC LIMIT 1")
    Optional<Auction> findCurrentByRef(@Param("ref") Long ref);

    @Query("""
            SELECT * FROM auction a
            WHERE a.id = (SELECT MAX(a2.id) FROM auction a2 WHERE a2.auction_ref = a.auction_ref)
              AND a.lifecycle_status = :status
            ORDER BY a.auction_ref
            """)
    List<Auction> findCurrentByLifecycleStatus(@Param("status") String status);

    @Query("""
            SELECT * FROM auction a
            WHERE a.id = (SELECT MAX(a2.id) FROM auction a2 WHERE a2.auction_ref = a.auction_ref)
              AND a.lifecycle_status = :status
              AND a.ends_at < :instant
            """)
    List<Auction> findCurrentByLifecycleStatusAndEndsAtBefore(@Param("status") String status,
                                                              @Param("instant") Instant instant);

    @Query("""
            SELECT * FROM auction a
            WHERE a.id = (SELECT MAX(a2.id) FROM auction a2 WHERE a2.auction_ref = a.auction_ref)
              AND (:status IS NULL OR a.lifecycle_status = :status)
            ORDER BY a.auction_ref
            LIMIT :limit OFFSET :offset
            """)
    List<Auction> findCurrentPage(@Param("status") String status,
                                  @Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT count(*) FROM auction a
            WHERE a.id = (SELECT MAX(a2.id) FROM auction a2 WHERE a2.auction_ref = a.auction_ref)
              AND (:status IS NULL OR a.lifecycle_status = :status)
            """)
    long countCurrent(@Param("status") String status);

    @Query("SELECT auction_ref FROM auction WHERE item_id = :itemId ORDER BY id DESC LIMIT 1")
    Optional<Long> findRefByItemId(@Param("itemId") String itemId);

    @Query("SELECT nextval('auction_ref_seq')")
    long nextAuctionRef();

}
