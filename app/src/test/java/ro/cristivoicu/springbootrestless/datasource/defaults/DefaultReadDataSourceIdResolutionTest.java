package ro.cristivoicu.springbootrestless.datasource.defaults;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test: {@code DefaultReadDataSource.findOne(Specification, K)} must resolve an
 * {@code @Id} field declared on a shared {@code @MappedSuperclass}, not just one declared
 * directly on the concrete entity — a common JPA pattern the original implementation (a single
 * {@code getDeclaredFields()} scan on the runtime class alone, no superclass walk) missed.
 * Mockito-backed, not a full Spring/JPA context, since the fix is pure reflection logic.
 */
class DefaultReadDataSourceIdResolutionTest {

    @MappedSuperclass
    static class BaseEntity {
        @Id
        private Long id;

        Long getId() {
            return id;
        }

        void setId(Long id) {
            this.id = id;
        }
    }

    static class ConcreteEntity extends BaseEntity {
        private String name;
    }

    static class NoOpSearchDto implements SearchDto {
        @Override
        public Pageable getPageable() {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private SpecificationRepository<ConcreteEntity, Long> repositoryReturning(ConcreteEntity entity) {
        SpecificationRepository<ConcreteEntity, Long> repository = mock(SpecificationRepository.class);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(entity));
        return repository;
    }

    @Test
    void resolvesIdDeclaredOnAMappedSuperclass() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.setId(42L);

        DefaultReadDataSource<ConcreteEntity, Long, NoOpSearchDto> dataSource =
                new DefaultReadDataSource<>(repositoryReturning(entity), NoOpSearchDto.class);

        ConcreteEntity found = dataSource.findOne((root, query, cb) -> cb.conjunction(), 42L);

        assertThat(found).isSameAs(entity);
    }

    @Test
    void returnsNullWhenTheMatchedEntitysIdDoesNotEqualTheRequestedId() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.setId(42L);

        DefaultReadDataSource<ConcreteEntity, Long, NoOpSearchDto> dataSource =
                new DefaultReadDataSource<>(repositoryReturning(entity), NoOpSearchDto.class);

        ConcreteEntity found = dataSource.findOne((root, query, cb) -> cb.conjunction(), 999L);

        assertThat(found).isNull();
    }
}
