package fi.petri.springauction.security;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
@Conditional(GoogleOAuthConfiguredCondition.class)
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        provisionIfAbsent(oidcUser.getSubject(), oidcUser.getEmail(), oidcUser.getFullName());
        return withUserRole(oidcUser);
    }

    OidcUser withUserRole(OidcUser oidcUser) {
        return new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo());
    }

    void provisionIfAbsent(String googleSubjectId, String email, String fullName) {
        userRepository.findByGoogleSubjectId(googleSubjectId)
                .orElseGet(() -> userRepository.save(new User(
                        null, googleSubjectId, email,
                        fullName != null ? fullName : email,
                        UserRole.USER, Instant.now())));
    }

}
