package ro.cristivoicu.springbootrestless.example.registry;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

/**
 * Bridges {@code @WithMockUser}/{@code SecurityMockMvcRequestPostProcessors.jwt()} into every
 * {@code @AutoConfigureMockMvc}-built {@code MockMvc} in this module. Without {@code
 * .apply(springSecurity())}, those annotations/post-processors populate {@code
 * TestSecurityContextHolder} but nothing ever copies that into the actual request the security
 * filter chain sees - every request still lands as anonymous, which is exactly why every
 * {@code /employees-dynamic} test kept getting 401 despite {@code @WithMockUser} until this was
 * added. Auto-detected via {@link MockMvcBuilderCustomizer} (no explicit wiring needed per test
 * class) since it's a plain {@code @Component} in this already-component-scanned package.
 */
@Component
class SecurityTestMockMvcCustomizer implements MockMvcBuilderCustomizer {

    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
