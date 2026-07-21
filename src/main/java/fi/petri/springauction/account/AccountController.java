package fi.petri.springauction.account;

import fi.petri.springauction.security.GoogleOAuthConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * My Account — the signed-in user's ongoing bids, won (with pay status), and lost auctions.
 * Login-gated by {@code appChain} (only {@code /} and static assets are public).
 */
@Controller
@Conditional(GoogleOAuthConfiguredCondition.class)
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/account")
    public String account(@AuthenticationPrincipal OidcUser principal, Model model) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        model.addAttribute("displayName", principal.getFullName() != null ? principal.getFullName() : principal.getEmail());
        model.addAttribute("account", accountService.myAccount(principal.getSubject()));
        return "account/account";
    }
}
