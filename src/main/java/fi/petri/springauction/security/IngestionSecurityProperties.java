package fi.petri.springauction.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionSecurityProperties(String apiKey) {
}
