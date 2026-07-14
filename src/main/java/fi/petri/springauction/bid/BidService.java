package fi.petri.springauction.bid;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final JdbcClient jdbcClient;
    private final Clock clock;

    public BidService(BidRepository bidRepository, AuctionRepository auctionRepository,
                       UserRepository userRepository, JdbcClient jdbcClient, Clock clock) {
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public Optional<Bid> findMyBid(Long auctionId, String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        return bidRepository.findById(new BidId(auctionId, user.id())).filter(bid -> !bid.isWithdrawn());
    }

    public Map<Long, Bid> findMyBidsByAuctionId(String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        return bidRepository.findByUserId(user.id()).stream()
                .filter(bid -> !bid.isWithdrawn())
                .collect(Collectors.toMap(bid -> bid.id().auctionId(), Function.identity()));
    }

    public void placeBid(Long auctionId, String googleSubjectId, BigDecimal amount) {
        User user = currentUser(googleSubjectId);
        Auction auction = requireOpenForBidding(auctionId);
        if (amount.compareTo(auction.startPrice()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bid must be at least the start price of " + auction.startPrice());
        }

        jdbcClient.sql("""
                        INSERT INTO bid (auction_id, user_id, amount, is_withdrawn, created_at, updated_at)
                        VALUES (:auctionId, :userId, :amount, false, now(), now())
                        ON CONFLICT (auction_id, user_id) DO UPDATE
                            SET amount       = EXCLUDED.amount,
                                is_withdrawn = false,
                                updated_at   = now()
                        """)
                .param("auctionId", auctionId)
                .param("userId", user.id())
                .param("amount", amount)
                .update();
    }

    public void withdrawBid(Long auctionId, String googleSubjectId) {
        User user = currentUser(googleSubjectId);
        requireOpenForBidding(auctionId);
        bidRepository.findById(new BidId(auctionId, user.id()))
                .filter(bid -> !bid.isWithdrawn())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active bid to withdraw"));

        jdbcClient.sql("UPDATE bid SET is_withdrawn = true, updated_at = now() WHERE auction_id = :auctionId AND user_id = :userId")
                .param("auctionId", auctionId)
                .param("userId", user.id())
                .update();
    }

    private Auction requireOpenForBidding(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
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
