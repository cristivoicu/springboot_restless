package ro.cristivoicu.springbootrestless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Test-scoped only (not part of the shipped {@code spring-boot-restless} library jar): {@code
 * app} is never run standalone - it's a library, self-tested via the fixtures under {@code
 * fixtures/} and {@code @SpringBootTest}s that need some {@code @SpringBootConfiguration} to
 * anchor on. Shipping this class in {@code src/main} instead would let a consumer's own {@code
 * @ComponentScan} sweep it up as a second, redundant auto-configuration entry point - which is
 * exactly what broke {@code example} the first time this lived under {@code src/main}.
 */
@SpringBootApplication
public class SpringBootRestlessApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootRestlessApplication.class, args);
    }

    /**
     * A resolution strategy is exactly what {@code @RestlessEntity(version = ...)}/{@code
     * @RestlessResource(version = ...)} deliberately don't configure themselves (see their
     * javadoc) - some app-wide choice has to exist for any versioned route to even register
     * ({@code RequestMappingInfo.Builder.version(...)} throws {@code IllegalStateException} at
     * startup otherwise, as this reactor's own {@link ro.cristivoicu.springbootrestless.fixtures.doohickey.Doohickey}
     * - {@code version = "1"} - would without this). Header-based, the simplest strategy to
     * exercise from a test; see {@code DynamicRouteVersionTest}.
     */
    @Bean
    public WebMvcConfigurer apiVersioningConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureApiVersioning(ApiVersionConfigurer configurer) {
                // Deliberately NOT detectSupportedVersions(true): that scans routes at the same
                // early phase RequestMappingHandlerMapping discovers ordinary @RequestMapping
                // beans - before RestlessRegistrar's own SmartInitializingSingleton has run, so
                // it would never see a version any @RestlessEntity/@RestlessResource declares.
                // addSupportedVersions(...) has to be listed by hand instead - the one real
                // limitation worth knowing about dynamically-registered versioned routes.
                configurer.useRequestHeader("X-API-Version")
                        // setVersionRequired(false): every other fixture/route in this reactor
                        // has no version constraint at all and must keep working exactly as
                        // before this strategy existed - only routes that actually declare one
                        // (Doohickey here) should ever need the header.
                        .setVersionRequired(false)
                        .addSupportedVersions("1");
            }
        };
    }

}
