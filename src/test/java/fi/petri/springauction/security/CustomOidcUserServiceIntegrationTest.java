package fi.petri.springauction.security;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
@Transactional
class CustomOidcUserServiceIntegrationTest {

    @Autowired
    CustomOidcUserService customOidcUserService;

    @Autowired
    UserRepository userRepository;

    @Test
    void firstLoginProvisionsAUserWithUserRole() {
        customOidcUserService.provisionIfAbsent("google-subject-1", "jane@example.com", "Jane Doe");

        List<User> matches = userRepository.findAll().stream()
                .filter(u -> "google-subject-1".equals(u.googleSubjectId()))
                .toList();
        assertEquals(1, matches.size());
        assertEquals("jane@example.com", matches.get(0).email());
        assertEquals("Jane Doe", matches.get(0).displayName());
        assertEquals(UserRole.USER, matches.get(0).role());
    }

    @Test
    void repeatedLoginDoesNotDuplicateTheUser() {
        customOidcUserService.provisionIfAbsent("google-subject-2", "john@example.com", "John Doe");
        customOidcUserService.provisionIfAbsent("google-subject-2", "john@example.com", "John Doe");

        long count = userRepository.findAll().stream()
                .filter(u -> "google-subject-2".equals(u.googleSubjectId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    void fullNameFallsBackToEmailWhenAbsent() {
        customOidcUserService.provisionIfAbsent("google-subject-3", "noname@example.com", null);

        User user = userRepository.findByGoogleSubjectId("google-subject-3").orElseThrow();
        assertEquals("noname@example.com", user.displayName());
    }

}
