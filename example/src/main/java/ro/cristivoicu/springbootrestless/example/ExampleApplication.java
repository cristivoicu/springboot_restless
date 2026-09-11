package ro.cristivoicu.springbootrestless.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

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
 */
@SpringBootApplication
@ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
