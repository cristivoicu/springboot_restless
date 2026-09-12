package ro.cristivoicu.springbootrestless.cerbos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only bootstrap class, same reasoning as {@code app}'s own test-scoped
 * {@code @SpringBootApplication}: this module is a library (no {@code src/main} entry point),
 * but {@code @DataJpaTest}/{@code @SpringBootTest} need a {@code @SpringBootConfiguration} to
 * find by walking up from the test class's package - placed at this package's root so every test
 * below it (including {@code fixtures}) is in scope.
 */
@SpringBootApplication
public class CerbosTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CerbosTestApplication.class, args);
    }
}
