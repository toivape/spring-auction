package fi.petri.springauction.admin;

import fi.petri.springauction.TestcontainersConfiguration;
import fi.petri.springauction.security.AdminBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminHomeControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminBootstrapProperties adminBootstrapProperties;

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password(adminBootstrapProperties.password()))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void loggedInAdminSeesTheHomePage() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(adminBootstrapProperties.email())));
    }

    @Test
    void loggingInRedirectsToAdminHome() throws Exception {
        mockMvc.perform(formLogin("/admin/login")
                        .user(adminBootstrapProperties.email())
                        .password(adminBootstrapProperties.password()))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void anonymousRequestToAdminHomeRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection());
    }

}
