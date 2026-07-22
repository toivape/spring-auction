package fi.petri.springauction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the health check is meaningful: with details revealed, the aggregate includes the database
 * connectivity indicator (so a DB outage would flip the endpoint to DOWN). Production keeps details
 * hidden (see {@link ActuatorHealthIntegrationTest}); this class overrides that only to assert wiring.
 */
@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActuatorHealthDbIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthIncludesTheDatabaseIndicator() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}
