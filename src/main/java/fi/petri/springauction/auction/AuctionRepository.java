package fi.petri.springauction.auction;

import org.springframework.data.repository.ListCrudRepository;

import java.time.Instant;
import java.util.List;

public interface AuctionRepository extends ListCrudRepository<Auction, Long> {

    List<Auction> findByLifecycleStatus(AuctionLifecycleStatus lifecycleStatus);

    List<Auction> findByLifecycleStatusAndEndsAtBefore(AuctionLifecycleStatus lifecycleStatus, Instant instant);

}
