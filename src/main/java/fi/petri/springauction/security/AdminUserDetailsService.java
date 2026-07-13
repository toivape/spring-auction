package fi.petri.springauction.security;

import fi.petri.springauction.user.User;
import fi.petri.springauction.user.UserRepository;
import fi.petri.springauction.user.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminCredentialRepository adminCredentialRepository;

    public AdminUserDetailsService(UserRepository userRepository, AdminCredentialRepository adminCredentialRepository) {
        this.userRepository = userRepository;
        this.adminCredentialRepository = adminCredentialRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .filter(candidate -> candidate.role() == UserRole.ADMIN)
                .orElseThrow(() -> new UsernameNotFoundException(email));

        AdminCredential credential = adminCredentialRepository.findById(user.id())
                .orElseThrow(() -> new UsernameNotFoundException(email));

        return new org.springframework.security.core.userdetails.User(
                user.email(), credential.passwordHash(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

}
