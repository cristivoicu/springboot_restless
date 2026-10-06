package ro.cristivoicu.springbootrestless.cerbos.fixtures;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Dedicated fixture for {@link ro.cristivoicu.springbootrestless.cerbos.CerbosQueryPlanTranslator}'s
 * {@code coerce()} widening a {@code String} literal (the only shape Cerbos's own {@code Value}
 * can carry for any of these) to the attribute's real JPA type - separate from {@link Widget} so
 * adding these fields never touches that fixture's existing all-args constructor call sites.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Status status;

    private UUID externalId;

    private Instant installedAt;

    public enum Status {
        ACTIVE, INACTIVE
    }
}
