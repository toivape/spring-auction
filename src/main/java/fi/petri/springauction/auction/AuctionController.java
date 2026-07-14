package fi.petri.springauction.auction;

import fi.petri.springauction.bid.Bid;
import fi.petri.springauction.bid.BidService;
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

@Controller
@Conditional(GoogleOAuthConfiguredCondition.class)
public class AuctionController {

    private final AuctionService auctionService;
    private final BidService bidService;

    public AuctionController(AuctionService auctionService, BidService bidService) {
        this.auctionService = auctionService;
        this.bidService = bidService;
    }

    @GetMapping("/")
    public String marketplace(@AuthenticationPrincipal OidcUser principal, Model model) {
        requireAuthenticated(principal);
        model.addAttribute("displayName", displayName(principal));
        model.addAttribute("auctions", auctionService.findActive());
        model.addAttribute("myBids", bidService.findMyBidsByAuctionId(principal.getSubject()));
        return "auction/marketplace";
    }

    @GetMapping("/auctions/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, Model model) {
        requireAuthenticated(principal);
        Auction auction = auctionService.findById(id);
        Bid myBid = bidService.findMyBid(id, principal.getSubject()).orElse(null);
        model.addAttribute("displayName", displayName(principal));
        model.addAttribute("auction", auction);
        model.addAttribute("myBid", myBid);
        return "auction/detail";
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
