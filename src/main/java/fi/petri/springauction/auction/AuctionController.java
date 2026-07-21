package fi.petri.springauction.auction;

import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidService;
import fi.petri.springauction.result.AuctionOutcomeView;
import fi.petri.springauction.result.AuctionResultService;
import fi.petri.springauction.result.ResultStatus;
import fi.petri.springauction.security.GoogleOAuthConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Controller
@Conditional(GoogleOAuthConfiguredCondition.class)
public class AuctionController {

    private final AuctionService auctionService;
    private final BidService bidService;
    private final AuctionResultService resultService;

    public AuctionController(AuctionService auctionService, BidService bidService,
                            AuctionResultService resultService) {
        this.auctionService = auctionService;
        this.bidService = bidService;
        this.resultService = resultService;
    }

    @GetMapping("/")
    public String marketplace(@AuthenticationPrincipal OidcUser principal, Model model) {
        boolean authenticated = principal != null;
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("displayName", authenticated ? displayName(principal) : null);
        model.addAttribute("auctions", auctionService.findActive());
        model.addAttribute("myBids", authenticated
                ? bidService.findMyBidsByAuctionId(principal.getSubject())
                : Map.of());
        return "auction/marketplace";
    }

    @GetMapping("/auctions/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, Model model) {
        requireAuthenticated(principal);
        Auction auction = auctionService.findById(id);
        Bid myBid = bidService.findMyBid(id, principal.getSubject()).orElse(null);
        AuctionOutcomeView outcome = resultService.outcomeFor(id, principal.getSubject()).orElse(null);
        model.addAttribute("displayName", displayName(principal));
        model.addAttribute("auction", auction);
        model.addAttribute("myBid", myBid);
        model.addAttribute("outcome", outcome);
        model.addAttribute("phase", detailPhase(auction, outcome));
        return "auction/detail";
    }

    /** Which state the detail page renders: the live bid form (OPEN) or a post-window/outcome message. */
    private String detailPhase(Auction auction, AuctionOutcomeView outcome) {
        Instant now = Instant.now();
        boolean active = auction.lifecycleStatus() == AuctionLifecycleStatus.ACTIVE;
        if (active && auction.startsAt() != null && auction.endsAt() != null
                && !now.isBefore(auction.startsAt()) && !now.isAfter(auction.endsAt())) {
            return "OPEN";
        }
        if (active && auction.startsAt() != null && now.isBefore(auction.startsAt())) {
            return "PENDING";
        }
        if (outcome != null && outcome.currentUserWon()) {
            return "WON";
        }
        if (outcome != null && outcome.status() == ResultStatus.SOLD) {
            return "LOST";
        }
        if (auction.lifecycleStatus() == AuctionLifecycleStatus.UNSOLD) {
            return "UNSOLD";
        }
        return "CLOSED";
    }

    @PostMapping("/auctions/{id}/bids")
    public String placeBid(@PathVariable Long id, @RequestParam BigDecimal amount,
                            @AuthenticationPrincipal OidcUser principal) {
        requireAuthenticated(principal);
        bidService.placeBid(id, principal.getSubject(), amount);
        return "redirect:/auctions/" + id;
    }

    @PostMapping("/auctions/{id}/bids/withdraw")
    public String withdrawBid(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal) {
        requireAuthenticated(principal);
        bidService.withdrawBid(id, principal.getSubject());
        return "redirect:/auctions/" + id;
    }

    private void requireAuthenticated(OidcUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private String displayName(OidcUser principal) {
        return principal.getFullName() != null ? principal.getFullName() : principal.getEmail();
    }

}
