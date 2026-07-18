package fi.petri.springauction.result;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidRepository;
import fi.petri.springauction.notification.AuctionFinalizedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Winner-selection finalization for auctions that end <em>with</em> bids. Auctions that end with no
 * active bid are handled separately by {@code AuctionUnsoldFinalizationJob} (UNSOLD, no result row) —
 * the two paths deliberately do not overlap: this service is a no-op when there are no eligible bids.
 */
@Service
public class AuctionFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(AuctionFinalizationService.class);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionResultRepository resultRepository;
    private final AuctionFinalizationService self;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AuctionFinalizationService(AuctionRepository auctionRepository, BidRepository bidRepository,
                                      AuctionResultRepository resultRepository,
                                      @Lazy AuctionFinalizationService self,
                                      ApplicationEventPublisher eventPublisher, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.resultRepository = resultRepository;
        this.self = self;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Finalizes every ended, still-ACTIVE auction. Each auction is finalized in its own transaction
     * (via the proxied {@link #self}) so one failure doesn't abort the rest of the batch.
     */
    public void finalizeEndedAuctions() {
        Instant now = Instant.now(clock);
        for (Auction auction : auctionRepository.findByLifecycleStatusAndEndsAtBefore(AuctionLifecycleStatus.ACTIVE, now)) {
            try {
                self.finalizeAuction(auction.id());
            } catch (Exception e) {
                log.warn("Finalization failed for auction {}: {}", auction.id(), e.getMessage(), e);
            }
        }
    }

    /**
     * Finalizes a single auction as SOLD if it has eligible bids. Idempotent: a no-op if the auction is
     * no longer ACTIVE, has not yet ended, or already has a result row. No-op (leaving the UNSOLD path to
     * the unsold job) if there are no eligible bids.
     */
    @Transactional
    public void finalizeAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || auction.lifecycleStatus() != AuctionLifecycleStatus.ACTIVE) {
            return;
        }
        if (auction.endsAt().isAfter(Instant.now(clock))) {
            return;
        }
        if (resultRepository.existsByAuctionId(auctionId)) {
            return;
        }

        List<Bid> eligible = bidRepository.findEligibleBids(auctionId);
        if (eligible.isEmpty()) {
            return;
        }

        Outcome outcome = computeOutcome(eligible, auction.auctionType(), auction.startPrice());
        resultRepository.save(new AuctionResult(
                null, auctionId, ResultStatus.SOLD, outcome.winnerUserId(), outcome.price(),
                Instant.now(clock), null, null, null, null, null));

        auctionRepository.save(new Auction(
                auction.id(), auction.itemId(), auction.title(), auction.description(), auction.category(),
                auction.auctionType(), AuctionLifecycleStatus.SOLD, auction.startPrice(), auction.currentValue(),
                auction.currency(), auction.startsAt(), auction.endsAt(), auction.comment(), auction.serialNumber(),
                auction.createdAt()));

        log.info("Finalized auction {} as SOLD: winner={}, price={}", auctionId, outcome.winnerUserId(), outcome.price());

        // Buffered until commit; the AFTER_COMMIT listener sends win/lose emails only once the result durably lands.
        String title = auction.title() != null ? auction.title() : auction.itemId();
        eventPublisher.publishEvent(new AuctionFinalizedEvent(
                auctionId, title, auction.currency(), outcome.winnerUserId(), outcome.price(),
                eligible.stream().map(Bid::userId).toList()));
    }

    /**
     * Winner and price over the eligible bids. Ranked by amount descending, then lowest bid id
     * (earliest to reach the amount) as the tie-break. FIRST_PRICE: winner pays their own amount.
     * SECOND_PRICE (Vickrey): winner pays the second-highest amount, or the start price if they were
     * the only bidder. Caller guarantees {@code eligible} is non-empty.
     */
    static Outcome computeOutcome(List<Bid> eligible, String auctionType, BigDecimal startPrice) {
        List<Bid> ranked = eligible.stream()
                .sorted(Comparator.comparing(Bid::amount).reversed().thenComparing(Bid::id))
                .toList();
        Bid winner = ranked.getFirst();

        BigDecimal price = "SECOND_PRICE".equals(auctionType)
                ? (ranked.size() >= 2 ? ranked.get(1).amount() : startPrice)
                : winner.amount();

        return new Outcome(winner.userId(), price);
    }

    record Outcome(Long winnerUserId, BigDecimal price) {
    }

}
