package ro.cristivoicu.springbootrestless.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * A standalone consumer of the {@code spring-boot-restless} library, exactly as an external
 * project would use it: this application's own code lives under {@code
 * ro.cristivoicu.springbootrestless.example}, a sibling of the framework's packages (.registry,
 * .resource, .datasource.defaults, ...), not a subpackage of them - so the default
 * {@code @SpringBootApplication} component scan (which only covers this class's own package and
 * below) wouldn't find the framework's beans (RestlessRegistrar, etc.) on its own. The explicit
 * {@code @ComponentScan} below is exactly the wiring a real consumer needs to add.
 * <p>
 * JPA entity scanning needs no such extra config: the example's entities live under {@code
 * .example.entity.*}, already below this class's own package, so Spring Boot's default entity
 * scan (which uses this class's package as its base) finds them without help.
 * <p>
 * {@code @EnableJpaAuditing}: the consumer-side half of opting an entity into {@code
 * AbstractAuditableEntity} (see {@code Project}, this codebase's one demo of it) -
 * deliberately not something {@code app} itself ever enables unilaterally, since it's an
 * application-wide decision. Framework-agnostic; nothing here is Restless-specific.
 */
@SpringBootApplication
@ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")
@EnableJpaAuditing
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }

    /**
     * The app-wide half of API versioning - {@code Project}'s {@code @RestlessEntity(version =
     * "1")} only declares *which* version its routes belong to; *how* a request's version is
     * resolved is this bean's job, same as any hand-written {@code @RequestMapping(version =
     * ...)} controller would need. Exact shape as {@code app}'s own test-scoped {@code
     * apiVersioningConfigurer()} (see its javadoc for why {@code detectSupportedVersions(true)}
     * can't be used instead - it scans before {@code RestlessRegistrar} has registered anything).
     * {@code setVersionRequired(false)}: every other route in this app declares no version at all
     * and must keep working with no {@code X-API-Version} header sent.
     */
    @Bean
    public WebMvcConfigurer apiVersioningConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureApiVersioning(ApiVersionConfigurer configurer) {
                configurer.useRequestHeader("X-API-Version")
                        .setVersionRequired(false)
                        .addSupportedVersions("1");
            }
        };
    }
}
