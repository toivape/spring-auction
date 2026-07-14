package fi.petri.springauction.auction;

import fi.petri.springauction.bid.BidRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final Clock clock;

    public AuctionService(AuctionRepository auctionRepository, BidRepository bidRepository, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.clock = clock;
    }

    public List<Auction> findAll() {
        return auctionRepository.findAll();
    }

    public List<Auction> findActive() {
        return auctionRepository.findByLifecycleStatus(AuctionLifecycleStatus.ACTIVE);
    }

    public Auction findById(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + auctionId));
    }

    public void activate(Long auctionId, Instant startsAt, Instant endsAt) {
        Auction auction = findById(auctionId);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionId + " is not in DRAFT status");
        }

        Instant resolvedStartsAt = startsAt != null ? startsAt : Instant.now(clock);
        Instant resolvedEndsAt = endsAt != null ? endsAt : resolvedStartsAt.plus(Duration.ofDays(30));

        auctionRepository.save(new Auction(
                auction.id(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), AuctionLifecycleStatus.ACTIVE, auction.startPrice(), auction.currentValue(),
                auction.currency(), resolvedStartsAt, resolvedEndsAt, auction.comment(), auction.serialNumber(),
                auction.createdAt()
        ));
    }

    public void archive(Long auctionId) {
        Auction auction = findById(auctionId);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionId + " is not in ACTIVE status");
        }
        if (auction.endsAt() == null || auction.endsAt().isAfter(Instant.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionId + " has not ended yet");
        }
        if (bidRepository.existsActiveBidForAuction(auctionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionId + " has bids, cannot archive");
        }

        auctionRepository.save(new Auction(
                auction.id(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), AuctionLifecycleStatus.ARCHIVED, auction.startPrice(), auction.currentValue(),
                auction.currency(), auction.startsAt(), auction.endsAt(), auction.comment(), auction.serialNumber(),
                auction.createdAt()
        ));
    }

}
