package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: {@code @RestlessEntity(version = "1")} on {@code Doohickey} reaches the generated
 * {@code @RestlessResource(version = "1")} and, through {@code RestlessRegistrar}, every route it
 * registers - real {@code RequestMappingInfo.Builder.version(...)} routing, not just a stored
 * string. {@code SpringBootRestlessApplication}'s {@code apiVersioningConfigurer()} resolves the
 * version from an {@code X-API-Version} header and doesn't require one on every other route in
 * this reactor's test fixtures (all unversioned, so they must keep working exactly as before this
 * strategy existed - see {@code RestlessRegistrarFullRouteTest} etc., unaffected).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DynamicRouteVersionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void matchingVersionHeaderReachesTheRoute() throws Exception {
        mockMvc.perform(get("/doohickeys").header("X-API-Version", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void noVersionHeaderStillReachesTheRoute() throws Exception {
        // setVersionRequired(false): a request that never resolves a version at all is not
        // rejected outright - it's treated as compatible with whichever candidate route Spring's
        // own version-comparison logic picks (the declared version, absent anything higher).
        mockMvc.perform(get("/doohickeys"))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedVersionHeaderIsRejectedNotSilentlyRoutedToVersionOne() throws Exception {
        // "2" isn't in apiVersioningConfigurer()'s addSupportedVersions(...) at all (only "1" -
        // Doohickey's own declared version - is) - Spring rejects it outright (400) rather than
        // falling through to the "1" route, proving the version constraint is genuinely enforced,
        // not just stored.
        mockMvc.perform(get("/doohickeys").header("X-API-Version", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unversionedFixtureRoutesAreUnaffectedByTheGlobalStrategy() throws Exception {
        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/gadgets-dynamic").header("X-API-Version", "1"))
                .andExpect(status().isOk());
    }
}
