package ro.cristivoicu.springbootrestless.fixtures.rivet;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;

import java.time.Instant;

/**
 * Manual-tier fixture for {@code MassAssignmentProtectionTest}: carries every protected-field
 * category {@code Default{Create,Update,Patch}DataSource} must shield from client-controlled DTO
 * values in one entity - {@code @Id}, {@code @Version}, {@link SoftDeletable}'s {@code deleted},
 * and the two audit-timestamp annotations {@code AbstractAuditableEntity} also uses. Declared
 * directly here (not via that mapped superclass) so a test can set canary values on them with a
 * plain Lombok setter, without needing a real {@code @EnableJpaAuditing} listener running.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rivet implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Version
    private Long version;

    private boolean deleted;

    @CreatedDate
    private Instant createdDate;

    @LastModifiedDate
    private Instant lastModifiedDate;
}
