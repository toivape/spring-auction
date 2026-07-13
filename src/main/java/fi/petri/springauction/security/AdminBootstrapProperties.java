package fi.petri.springauction.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(String email, String password) {
}
