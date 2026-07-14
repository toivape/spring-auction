package fi.petri.springauction.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOidcUserServiceTest {

    @Test
    void withUserRoleGrantsRoleUser() {
        CustomOidcUserService service = new CustomOidcUserService(null);
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "google-subject", "email", "jane@example.com"));
        OidcUser fakeUser = new DefaultOidcUser(Set.of(), idToken);

        OidcUser result = service.withUserRole(fakeUser);

        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

}
