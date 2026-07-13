package fi.petri.springauction.security;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds/re-hashes the admin login on every startup so the real password
 * never needs to be committed anywhere (dev default only, see application.yaml).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final JdbcClient jdbcClient;
    private final AdminBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 JdbcClient jdbcClient,
                                 AdminBootstrapProperties properties,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jdbcClient = jdbcClient;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail(properties.email())
                .orElseGet(() -> userRepository.save(
                        new User(null, null, properties.email(), "Admin", UserRole.ADMIN, Instant.now())));

        jdbcClient.sql("""
                        INSERT INTO admin_credential (user_id, password_hash, updated_at)
                        VALUES (:userId, :passwordHash, now())
                        ON CONFLICT (user_id) DO UPDATE
                            SET password_hash = EXCLUDED.password_hash,
                                updated_at    = now()
                        """)
                .param("userId", admin.id())
                .param("passwordHash", passwordEncoder.encode(properties.password()))
                .update();
    }

}
