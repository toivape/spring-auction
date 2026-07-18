package fi.petri.springauction.result;

import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface AuctionResultRepository extends ListCrudRepository<AuctionResult, Long> {

    boolean existsByAuctionId(Long auctionId);

    Optional<AuctionResult> findByAuctionId(Long auctionId);

}
