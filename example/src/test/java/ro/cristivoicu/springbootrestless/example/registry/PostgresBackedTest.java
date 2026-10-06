package ro.cristivoicu.springbootrestless.example.registry;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Ground rules Phase 3 item 17 ("Tooling"): a real PostgreSQL database for whichever example
 * tests need to run against one - the point is catching the exact class of bug H2's own
 * emulation papers over (its {@code LIKE} wildcard/collation/null-ordering semantics don't always
 * match a real Postgres server), not replacing the fast default H2-backed run everywhere else in
 * this module.
 * <p>
 * A plain {@link GenericContainer}, not the dedicated {@code org.testcontainers:postgresql}
 * module's {@code PostgreSQLContainer}: at the time of writing that module has no release
 * compatible with {@code testcontainers-junit-jupiter}'s 2.0.x line this reactor already depends
 * on (its own latest is still 1.21.x) - the same {@link GenericContainer} idiom {@code
 * cerbos-sdk-java}'s own {@code CerbosContainer} already uses elsewhere in this reactor.
 * <p>
 * {@code extends CerbosBackedTest}, not a sibling base class: every entity in this module already
 * goes through a real {@code CerbosAuthorizationGuard} (see {@link CerbosBackedTest}'s own
 * javadoc), so a Postgres-backed test still needs that same PDP wired in underneath it. Same
 * "singleton container started once in a static initializer, {@link DynamicPropertySource} wires
 * its connection details in before each subclass's context starts" pattern {@link
 * CerbosBackedTest} already uses, and for the same reason: a container meant to outlive any one
 * test class can't be {@code @Testcontainers}/{@code @Container}-managed without it (and the
 * {@code ApplicationContext} built against it) being torn down the moment the first subclass
 * finishes.
 */
abstract class PostgresBackedTest extends CerbosBackedTest {

    private static final String DATABASE = "restless";
    private static final String USERNAME = "restless";
    private static final String PASSWORD = "restless";

    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv("POSTGRES_DB", DATABASE)
            .withEnv("POSTGRES_USER", USERNAME)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/" + DATABASE);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
