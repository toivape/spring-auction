package fi.petri.springauction.bid;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public BidService(BidRepository bidRepository, AuctionRepository auctionRepository,
                       UserRepository userRepository, Clock clock) {
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public Optional<Bid> findMyBid(Long auctionId, String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        return bidRepository.findCurrentBid(auctionId, user.id()).filter(Bid::isActive);
    }

    public Map<Long, Bid> findMyBidsByAuctionId(String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        return bidRepository.findCurrentActiveBidsForUser(user.id()).stream()
                .collect(Collectors.toMap(Bid::auctionId, Function.identity()));
    }

    public void placeBid(Long auctionId, String googleSubjectId, BigDecimal amount) {
        User user = currentUser(googleSubjectId);
        Auction auction = requireOpenForBidding(auctionId);
        if (amount.compareTo(auction.startPrice()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bid must be at least the start price of " + auction.startPrice());
        }

        boolean hadActiveBid = bidRepository.findCurrentBid(auctionId, user.id()).filter(Bid::isActive).isPresent();
        BidEventType eventType = hadActiveBid ? BidEventType.CHANGED : BidEventType.PLACED;
        append(auctionId, user.id(), eventType, amount, user.id(), null);
    }

    public void withdrawBid(Long auctionId, String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        requireOpenForBidding(auctionId);
        Bid current = bidRepository.findCurrentBid(auctionId, user.id())
                .filter(Bid::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active bid to withdraw"));

        append(auctionId, user.id(), BidEventType.WITHDRAWN, current.amount(), user.id(), null);
    }

    private void append(Long auctionId, Long userId, BidEventType eventType, BigDecimal amount,
                         Long actorUserId, String reason) {
        bidRepository.save(new Bid(null, auctionId, userId, eventType, amount, actorUserId, reason, Instant.now(clock)));
    }

    private Auction requireOpenForBidding(Long auctionId) {
        Auction auction = auctionRepository.findCurrentByRef(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + auctionId));

        Instant now = Instant.now(clock);
        if (auction.lifecycleStatus() != AuctionLifecycleStatus.ACTIVE
                || auction.startsAt() == null || auction.endsAt() == null
                || now.isBefore(auction.startsAt()) || now.isAfter(auction.endsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is not open for bidding");
        }
        return auction;
    }

    private User currentUser(String googleSubjectId) {
        return userRepository.findByGoogleSubjectId(googleSubjectId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not provisioned: " + googleSubjectId));
    }

}
