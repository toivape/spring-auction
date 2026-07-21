package fi.petri.springauction.account;

import fi.petri.springauction.account.AccountView.LostEntry;
import fi.petri.springauction.account.AccountView.OngoingEntry;
import fi.petri.springauction.account.AccountView.WonEntry;
import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.bid.BidRepository;
import fi.petri.springauction.result.AuctionResultRepository;
import fi.petri.springauction.result.ResultStatus;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionResultRepository resultRepository;
    private final UserRepository userRepository;

    public AccountService(BidRepository bidRepository, AuctionRepository auctionRepository,
                          AuctionResultRepository resultRepository, UserRepository userRepository) {
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
    }

    public AccountView myAccount(String googleSubjectId) {
        Long userId = userRepository.findByGoogleSubjectId(googleSubjectId)
                .map(User::id)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not provisioned: " + googleSubjectId));

        // Ongoing: the user's live bids on auctions that are still ACTIVE.
        List<OngoingEntry> ongoing = bidRepository.findCurrentActiveBidsForUser(userId).stream()
                .map(bid -> auctionRepository.findCurrentByRef(bid.auctionId())
                        .filter(a -> a.lifecycleStatus() == AuctionLifecycleStatus.ACTIVE)
                        .map(a -> new OngoingEntry(a.auctionRef(), title(a), a.itemId(), bid.amount(),
                                a.currency(), a.endsAt())))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(OngoingEntry::endsAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Won: SOLD results where the user is the (still-valid) winner.
        List<WonEntry> won = resultRepository.findByWinnerUserId(userId).stream()
                .filter(r -> r.resultStatus() == ResultStatus.SOLD && r.invalidatedAt() == null)
                .map(r -> auctionRepository.findCurrentByRef(r.auctionId())
                        .map(a -> new WonEntry(a.auctionRef(), title(a), a.itemId(), r.winningPrice(),
                                a.currency(), r.paidAt() != null)))
                .flatMap(Optional::stream)
                .toList();

        // Lost: auctions the user bid on that sold to someone else.
        List<LostEntry> lost = resultRepository.findLostResultsForUser(userId).stream()
                .map(r -> auctionRepository.findCurrentByRef(r.auctionId())
                        .map(a -> new LostEntry(a.auctionRef(), title(a), a.itemId(), a.currency())))
                .flatMap(Optional::stream)
                .toList();

        return new AccountView(ongoing, won, lost);
    }

    private static String title(Auction auction) {
        return auction.title() != null ? auction.title() : auction.itemId();
    }
}
