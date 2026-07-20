package fi.petri.springauction.result;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuctionResultService {

    private final AuctionResultRepository resultRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AuctionResultService(AuctionResultRepository resultRepository, UserRepository userRepository, Clock clock) {
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * The finalized outcome for one auction as the given user should see it, or empty if the auction
     * isn't finalized with a result row (an UNSOLD auction has none — the caller handles that case).
     */
    public Optional<AuctionOutcomeView> outcomeFor(Long auctionId, String googleSubjectId) {
        return resultRepository.findByAuctionId(auctionId).map(result -> {
            Long userId = userRepository.findByGoogleSubjectId(googleSubjectId).map(User::id).orElse(null);
            boolean won = result.resultStatus() == ResultStatus.SOLD
                    && result.winnerUserId() != null
                    && result.winnerUserId().equals(userId);
            return new AuctionOutcomeView(
                    result.resultStatus(),
                    won,
                    won ? result.winningPrice() : null,
                    won && result.paidAt() != null);
        });
    }

    /**
     * Records payment for a finalized auction by stamping {@code paid_at}. Direct finalize — no gateway.
     * Only the winner of a live SOLD result may pay; re-paying is a safe no-op (the win email link can be
     * clicked more than once). {@code @Version} guards a concurrent double-pay.
     */
    @Transactional
    public void markPaid(Long auctionRef, String googleSubjectId) {
        Long userId = userRepository.findByGoogleSubjectId(googleSubjectId)
                .map(User::id)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not provisioned: " + googleSubjectId));

        AuctionResult result = resultRepository.findByAuctionId(auctionRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No result for auction " + auctionRef));

        if (result.resultStatus() != ResultStatus.SOLD || result.invalidatedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction " + auctionRef + " is not payable");
        }
        if (!userId.equals(result.winnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the winner may pay for auction " + auctionRef);
        }
        if (result.paidAt() != null) {
            return;
        }

        resultRepository.save(new AuctionResult(
                result.id(), result.auctionId(), result.resultStatus(), result.winnerUserId(),
                result.winningPrice(), result.finalizedAt(), Instant.now(clock),
                result.invalidatedAt(), result.invalidatedBy(), result.invalidationReason(), result.version()));
    }
}
