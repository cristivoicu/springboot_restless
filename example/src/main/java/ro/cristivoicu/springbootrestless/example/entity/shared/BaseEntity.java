package ro.cristivoicu.springbootrestless.example.entity.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration proof for the {@code @Id}-on-a-{@code @MappedSuperclass} fix ({@code
 * RestlessEntityProcessor#resolveIdType} now walks superclasses, not just the annotated entity
 * itself - see {@code RestlessEntityProcessorInheritedIdTest} in the {@code processor} module for
 * the isolated compile-time proof). Every other entity in this app declares its own {@code @Id}
 * directly, so without a fixture extending this, nothing in the reactor would exercise that fix
 * against a real {@code @RestlessEntity}, a real generated {@code SpecificationRepository<Skill,
 * UUID>}, and a real HTTP round trip.
 * <p>
 * Shape mirrors a genuinely common project layout (a shared base carrying identity + audit
 * columns, which is exactly the integration need that surfaced the processor bug in the first
 * place) - a generated {@code UUID} id via Hibernate's own {@link UuidGenerator}, plus the usual
 * {@code createdAt}/{@code updatedAt} timestamps via {@link CreationTimestamp}/{@link
 * UpdateTimestamp} (Hibernate's own annotations, not Spring Data JPA auditing - no {@code
 * @EnableJpaAuditing}/{@code AuditingEntityListener} needed, unlike {@link
 * ro.cristivoicu.springbootrestless.models.AbstractAuditableEntity}, this app's other, Spring-Data-
 * auditing-based convenience base).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
