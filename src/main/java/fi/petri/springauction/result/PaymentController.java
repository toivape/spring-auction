package fi.petri.springauction.result;

import fi.petri.springauction.security.GoogleOAuthConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pay Now — sets {@code paid_at} directly on a won auction's result, no external gateway. Same login-gated
 * flow whether reached from the detail page's WON block or the win email's deep link.
 */
@Controller
@Conditional(GoogleOAuthConfiguredCondition.class)
public class PaymentController {

    private final AuctionResultService resultService;

    public PaymentController(AuctionResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping("/auctions/{id}/pay")
    public String pay(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        resultService.markPaid(id, principal.getSubject());
        return "redirect:/auctions/" + id;
    }
}
