package fi.petri.springauction.auction;

import fi.petri.springauction.bid.BidRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

    public Page<Auction> findPage(AuctionLifecycleStatus statusFilter, Pageable pageable) {
        String status = statusFilter != null ? statusFilter.name() : null;
        List<Auction> content = auctionRepository.findCurrentPage(status, pageable.getPageSize(), pageable.getOffset());
        long total = auctionRepository.countCurrent(status);
        return new PageImpl<>(content, pageable, total);
    }

    public List<Auction> findActive() {
        return auctionRepository.findCurrentByLifecycleStatus(AuctionLifecycleStatus.ACTIVE.name());
    }

    public Auction findById(Long auctionRef) {
        return auctionRepository.findCurrentByRef(auctionRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + auctionRef));
    }

    public void activate(Long auctionRef, Instant startsAt, Instant endsAt) {
        Auction auction = findById(auctionRef);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionRef + " is not in DRAFT status");
        }

        Instant resolvedStartsAt = startsAt != null ? startsAt : Instant.now(clock);
        Instant resolvedEndsAt = endsAt != null ? endsAt : resolvedStartsAt.plus(Duration.ofDays(30));

        appendVersion(auction, AuctionLifecycleStatus.ACTIVE, auction.startPrice(), auction.currentValue(),
                resolvedStartsAt, resolvedEndsAt);
    }

    public void archive(Long auctionRef) {
        Auction auction = findById(auctionRef);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.UNSOLD
                && auction.lifecycleStatus() != AuctionLifecycleStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionRef + " is not in UNSOLD or CANCELLED status");
        }

        appendVersion(auction, AuctionLifecycleStatus.ARCHIVED, auction.startPrice(), auction.currentValue(),
                auction.startsAt(), auction.endsAt());
    }

    public void extend(Long auctionRef, Instant endsAt, BigDecimal startPrice) {
        Auction auction = findById(auctionRef);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.UNSOLD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionRef + " is not in UNSOLD status");
        }

        Instant resolvedEndsAt = endsAt != null ? endsAt : Instant.now(clock).plus(Duration.ofDays(30));
        // An UNSOLD auction has no active bids, so start price and current value move together; a blank
        // startPrice keeps the existing one (resolvedStartPrice == old startPrice == old currentValue).
        BigDecimal resolvedStartPrice = startPrice != null ? startPrice : auction.startPrice();
        if (resolvedStartPrice.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Start price must not be negative");
        }

        appendVersion(auction, AuctionLifecycleStatus.ACTIVE, resolvedStartPrice, resolvedStartPrice,
                auction.startsAt(), resolvedEndsAt);
    }

    public void cancel(Long auctionRef) {
        Auction auction = findById(auctionRef);

        if (auction.lifecycleStatus() != AuctionLifecycleStatus.DRAFT
                && auction.lifecycleStatus() != AuctionLifecycleStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auction " + auctionRef + " is not in DRAFT or ACTIVE status");
        }

        appendVersion(auction, AuctionLifecycleStatus.CANCELLED, auction.startPrice(), auction.currentValue(),
                auction.startsAt(), auction.endsAt());
    }

    public void finalizeUnsold() {
        Instant now = Instant.now(clock);
        for (Auction auction : auctionRepository.findCurrentByLifecycleStatusAndEndsAtBefore(
                AuctionLifecycleStatus.ACTIVE.name(), now)) {
            if (bidRepository.existsActiveBidForAuction(auction.auctionRef())) {
                continue;
            }
            appendVersion(auction, AuctionLifecycleStatus.UNSOLD, auction.startPrice(), auction.currentValue(),
                    auction.startsAt(), auction.endsAt());
        }
    }

    /**
     * Appends a new version row for the auction (same auction_ref, null @Id ⇒ INSERT), carrying every
     * field forward except the ones a transition changes.
     */
    private void appendVersion(Auction auction, AuctionLifecycleStatus status, BigDecimal startPrice,
                               BigDecimal currentValue, Instant startsAt, Instant endsAt) {
        auctionRepository.save(new Auction(
                null, auction.auctionRef(), auction.itemId(), auction.title(), auction.description(),
                auction.category(), auction.auctionType(), status, startPrice, currentValue,
                auction.currency(), startsAt, endsAt, auction.comment(), auction.serialNumber(),
                Instant.now(clock)));
    }

}
