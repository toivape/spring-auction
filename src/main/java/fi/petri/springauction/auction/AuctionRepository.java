package fi.petri.springauction.auction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.time.Instant;
import java.util.List;

public interface AuctionRepository extends ListCrudRepository<Auction, Long>, PagingAndSortingRepository<Auction, Long> {

    List<Auction> findByLifecycleStatus(AuctionLifecycleStatus lifecycleStatus);

    Page<Auction> findByLifecycleStatus(AuctionLifecycleStatus lifecycleStatus, Pageable pageable);

    List<Auction> findByLifecycleStatusAndEndsAtBefore(AuctionLifecycleStatus lifecycleStatus, Instant instant);

}
