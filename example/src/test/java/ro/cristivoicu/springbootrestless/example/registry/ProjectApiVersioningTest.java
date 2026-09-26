package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof of this app's one API-versioning demo: {@code Project}'s {@code @RestlessEntity(version =
 * "1")}, resolved by {@code ExampleApplication#apiVersioningConfigurer()} ({@code X-API-Version}
 * header, not required). {@code setVersionRequired(false)} is the point being proved here - a
 * request with no version header at all must still reach the route, exactly like every other
 * unversioned route in this app; a request naming the declared version must also reach it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectApiVersioningTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void routeIsReachableWithNoVersionHeaderAtAll() throws Exception {
        mockMvc.perform(get("/projects").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void routeIsReachableWithTheDeclaredVersionHeader() throws Exception {
        mockMvc.perform(get("/projects").header("X-API-Version", "1").with(admin()))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }
}
