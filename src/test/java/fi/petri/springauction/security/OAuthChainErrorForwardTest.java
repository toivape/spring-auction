package fi.petri.springauction.security;

import fi.petri.springauction.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bad/missing X-API-Key on /api/ingest must return 403 from ingestionChain even when appChain also
 * exists (GOOGLE_CLIENT_ID set). Regression test for the real ErrorPageFilter interaction: Spring Boot
 * forwards any sendError() to /error internally, which re-enters FilterChainProxy as a fresh request; if
 * /error isn't permitted on appChain's catch-all, that forwarded request gets denied there too and its
 * oauth2Login entry point silently replaces the original 403 with a 302 to Google.
 * <p>
 * Requires a real embedded server (RANDOM_PORT) and a real HTTP client — MockMvc never performs the
 * container-level error-page forward, so it can't reproduce this. Redirects must NOT be followed, since
 * a 302 (not a 403) is exactly the bug this test guards against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.google.client-id=test-client-id",
        "app.google.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
class OAuthChainErrorForwardTest {

    @LocalServerPort
    int port;

    @Autowired
    IngestionSecurityProperties ingestionSecurityProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void missingApiKeyReturns403NotOAuthRedirect() throws IOException, InterruptedException {
        assertEquals(403, postIngest(null).statusCode());
    }

    @Test
    void wrongApiKeyReturns403NotOAuthRedirect() throws IOException, InterruptedException {
        assertEquals(403, postIngest("wrong-key").statusCode());
    }

    @Test
    void correctApiKeyReachesController() throws IOException, InterruptedException {
        assertEquals(400, postIngest(ingestionSecurityProperties.apiKey()).statusCode());
    }

    private HttpResponse<String> postIngest(String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ingest"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (apiKey != null) {
            builder.header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

}
