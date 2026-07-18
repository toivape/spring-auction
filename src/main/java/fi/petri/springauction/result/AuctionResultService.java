package fi.petri.springauction.result;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuctionResultService {

    private final AuctionResultRepository resultRepository;
    private final UserRepository userRepository;

    public AuctionResultService(AuctionResultRepository resultRepository, UserRepository userRepository) {
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
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
}
