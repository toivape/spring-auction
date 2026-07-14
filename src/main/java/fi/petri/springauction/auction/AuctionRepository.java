package fi.petri.springauction.auction;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface AuctionRepository extends ListCrudRepository<Auction, Long> {

    List<Auction> findByLifecycleStatus(AuctionLifecycleStatus lifecycleStatus);

}
