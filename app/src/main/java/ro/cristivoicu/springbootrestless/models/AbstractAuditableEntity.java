package ro.cristivoicu.springbootrestless.models;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Opt-in convenience base for an entity that wants {@code createdDate}/{@code lastModifiedDate}
 * populated automatically via Spring Data JPA's own auditing infrastructure. Purely additive -
 * nothing elsewhere in this framework requires or assumes an entity extends this; every other
 * entity in this codebase stays flat, no mapped superclass at all, and that stays a valid choice.
 * <p>
 * Extending this is only half the setup: the consuming application still needs {@code
 * @EnableJpaAuditing} on one of its own {@code @Configuration}/{@code @SpringBootApplication}
 * classes for {@link AuditingEntityListener} to actually run - deliberately not auto-configured
 * here, since enabling JPA auditing is an application-wide decision this framework has no
 * business making unilaterally for every consumer the moment one entity opts in.
 * <p>
 * Deliberately no {@code createdBy}/{@code lastModifiedBy}: those need a {@code AuditorAware}
 * bean resolving "who is the current principal," which is exactly the kind of auth-stack-specific
 * concern this framework otherwise keeps out of {@code app} entirely (see {@code
 * AuthorizationGuard}'s own javadoc) - a consumer wanting them adds their own {@code AuditorAware}
 * and their own {@code @CreatedBy}/{@code @LastModifiedBy} fields on top of this base, or instead
 * of it.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity {

    @CreatedDate
    private Instant createdDate;

    @LastModifiedDate
    private Instant lastModifiedDate;
}
