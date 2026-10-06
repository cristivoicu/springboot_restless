package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ground rules Phase 2 item 9: {@code readVersion} must walk superclasses - a {@code @Version}
 * declared on a shared {@code @MappedSuperclass} (not uncommon - see {@code
 * AbstractAuditableEntity} for the same pattern with audit fields) was previously invisible to
 * {@code getDeclaredFields()} on the concrete subclass alone, silently disabling {@code If-Match}
 * support for any such entity.
 */
class PreconditionSupportVersionWalkTest {

    static class VersionedBase {
        @Version
        private Long version = 7L;
    }

    static class Concrete extends VersionedBase {
        private String name = "unrelated";
    }

    @Test
    void readVersionFindsAVersionFieldDeclaredOnAMappedSuperclass() {
        assertThat(PreconditionSupport.readVersion(new Concrete())).contains("7");
    }
}
