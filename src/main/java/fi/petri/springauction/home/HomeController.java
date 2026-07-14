package fi.petri.springauction.home;

import fi.petri.springauction.security.GoogleOAuthConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Conditional(GoogleOAuthConfiguredCondition.class)
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        model.addAttribute("displayName", principal.getFullName() != null ? principal.getFullName() : principal.getEmail());
        model.addAttribute("email", principal.getEmail());
        return "home";
    }

}
