package fi.petri.springauction.auction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;

    public AuctionService(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    public void activate(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + auctionId));

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionId + " is not in DRAFT status");
        }

        auctionRepository.save(new Auction(
                auction.id(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), AuctionLifecycleStatus.ACTIVE, auction.startPrice(), auction.currentValue(),
                auction.currency(), auction.startsAt(), auction.endsAt(), auction.comment(), auction.serialNumber(),
                auction.createdAt()
        ));
    }

}
