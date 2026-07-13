package fi.petri.springauction.auction;

import org.springframework.data.repository.ListCrudRepository;

public interface AuctionRepository extends ListCrudRepository<Auction, Long> {
}
