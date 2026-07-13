package fi.petri.springauction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final IngestionSecurityProperties properties;

    public ApiKeyAuthenticationFilter(IngestionSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String suppliedKey = request.getHeader(API_KEY_HEADER);
        if (suppliedKey != null && suppliedKey.equals(properties.apiKey())) {
            SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken());
        }
        filterChain.doFilter(request, response);
    }

    private static final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

        private ApiKeyAuthenticationToken() {
            super(List.of(new SimpleGrantedAuthority("ROLE_INGEST")));
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "ingestion-client";
        }
    }
}
