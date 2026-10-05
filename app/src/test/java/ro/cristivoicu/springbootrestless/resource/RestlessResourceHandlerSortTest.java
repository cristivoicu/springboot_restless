package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic unit tests for Ground rules item 4 ("Sorting") - deliberately not going through
 * MockMvc/a real entity: a tie in observed row order on a tiny H2 table coincidentally matches
 * insertion/id order whether or not a tie-breaker is actually appended, so the only way to
 * reliably prove the tie-breaker logic itself is to test the {@link Sort} object it produces
 * directly. {@code RestlessEntityIdSortTest} (same package as the real fixtures) separately
 * proves the end-to-end wiring: an entity whose {@code @Id} isn't literally named {@code "id"}
 * must still default-sort and validate correctly.
 */
class RestlessResourceHandlerSortTest {

    @Test
    void noExplicitSortDefaultsToTheResolvedIdProperty() {
        Sort result = RestlessResourceHandler.applyIdDefaultAndTieBreaker(List.of(), Sort.by(Sort.Direction.ASC, "id"), "code");

        assertThat(result.toList()).extracting(Sort.Order::getProperty).containsExactly("code");
    }

    @Test
    void explicitSortNotEndingInIdGetsTheIdAppendedAsATieBreaker() {
        Sort clientSort = Sort.by(Sort.Direction.DESC, "name");

        Sort result = RestlessResourceHandler.applyIdDefaultAndTieBreaker(List.of("name,desc"), clientSort, "id");

        assertThat(result.toList()).extracting(Sort.Order::getProperty).containsExactly("name", "id");
        assertThat(result.toList()).extracting(Sort.Order::getDirection)
                .containsExactly(Sort.Direction.DESC, Sort.Direction.ASC);
    }

    @Test
    void explicitSortAlreadyEndingInIdIsLeftUnchanged() {
        Sort clientSort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.DESC, "id"));

        Sort result = RestlessResourceHandler.applyIdDefaultAndTieBreaker(List.of("name,asc", "id,desc"), clientSort, "id");

        assertThat(result.toList()).extracting(Sort.Order::getProperty).containsExactly("name", "id");
        assertThat(result.toList()).extracting(Sort.Order::getDirection)
                .containsExactly(Sort.Direction.ASC, Sort.Direction.DESC);
    }

    @Test
    void sortablePropertiesExcludeAssociationsCollectionsStaticTransientAndMaskedFields() {
        Set<String> sortable = RestlessResourceHandler.sortablePropertyNames(SampleEntity.class, SampleDto.class);

        assertThat(sortable).containsExactlyInAnyOrder("id", "name");
    }

    @SuppressWarnings("unused")
    private static class SampleEntity {
        private Long id;
        private String name;

        @OneToMany
        private List<String> children;

        @ManyToOne
        private Object parent;

        private static String staticField = "static";

        private transient String scratch;

        @Transient
        private String computed;

        private String secret; // masked via SampleDto#secret's @CerbosHiddenField below
    }

    /**
     * A local stand-in for the real {@code ro.cristivoicu.springbootrestless.cerbos.CerbosHiddenField}
     * - "app" can't depend on the optional "cerbos" module, so {@code
     * RestlessResourceHandler#sortablePropertyNames} matches by annotation simple name, not type.
     * Same simple name as the real annotation is exactly what's under test here. {@code RUNTIME}
     * retention, matching the real annotation - reflection (what the code under test uses) can't
     * see a {@code CLASS}-retention (the default) annotation at all.
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface CerbosHiddenField {
    }

    @SuppressWarnings("unused")
    private static class SampleDto {
        @CerbosHiddenField
        private String secret;
        private String name;
    }
}
