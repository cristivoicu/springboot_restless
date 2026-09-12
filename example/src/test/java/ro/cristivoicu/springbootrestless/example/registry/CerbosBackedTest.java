package ro.cristivoicu.springbootrestless.example.registry;

import dev.cerbos.sdk.CerbosContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;

/**
 * Shared base for every test that exercises {@code /employees-dynamic}: since {@code
 * EmployeeRestlessResource} now delegates every action to a real {@link
 * ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard}, those routes need an actual
 * Cerbos PDP answering - not just an authenticated principal - to behave as before.
 * <p>
 * One {@link CerbosContainer}, started once in the static initializer below and shared by every
 * subclass, loaded with this module's real {@code src/main/resources/policies/employee.yaml}.
 * Deliberately <b>not</b> {@code @Testcontainers}/{@code @Container} - that annotation pair stops
 * the container in {@code afterAll} of whichever test class happens to finish first, which broke
 * every later subclass sharing this same static field (Spring's test-context cache kept reusing a
 * {@code CerbosBlockingClient} built against the now-dead container's port). This is
 * Testcontainers' documented "singleton container" pattern for exactly that reason: a container
 * meant to outlive any single test class needs to be started (and left running) outside any
 * per-class extension lifecycle. {@link DynamicPropertySource} wires its mapped port into {@code
 * cerbos.client.target} before each subclass's Spring context starts; since every subclass ends
 * up with the same property value, Spring's test context cache reuses one {@code
 * ApplicationContext} across all of them rather than restarting per class.
 */
abstract class CerbosBackedTest {

    static final CerbosContainer CERBOS = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", BindMode.READ_ONLY);

    static {
        CERBOS.start();
    }

    @DynamicPropertySource
    static void cerbosProperties(DynamicPropertyRegistry registry) {
        registry.add("cerbos.client.target", CERBOS::getTarget);
    }
}
