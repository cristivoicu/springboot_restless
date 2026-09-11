package ro.cristivoicu.springbootrestless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

}
