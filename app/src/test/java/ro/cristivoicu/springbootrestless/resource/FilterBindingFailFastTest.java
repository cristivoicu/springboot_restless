package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;
import ro.cristivoicu.springbootrestless.models.SearchDto;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ground rules item 5 ("Filter DSL hardening"): every one of these is now a startup-time
 * failure (via {@code RestlessResourceHandler#init} -&gt; {@code resolveFilterBindings}) rather
 * than a silently-ignored field or a failure thrown mid-query. Pure unit tests, no Spring
 * context - {@code init()} never touches the database, only reflects over the entity/SearchDto
 * class shapes. {@link DefaultReadDataSource} (not a hand-rolled {@code ReadDataSource}
 * subclass) is what lets {@code resolveMetadata()} recover each case's concrete {@code
 * SearchDto} type - it implements {@code TypedDataSource}, the same reason {@code
 * DefaultDataSourceTypeResolutionTest} already uses it instead of leaning on {@code
 * GenericTypeResolver} (which can't see a type argument an anonymous subclass's diamond
 * operator left for the compiler to infer).
 */
class FilterBindingFailFastTest {

    /** Deliberately not a real {@code @Entity} - these tests never touch a database, only reflection over this shape. */
    static class Thing {
        @Id
        private Long id;
        private String name;
        private List<String> tags;
    }

    static class GoodSearchDto extends AbstractSearchDto {
        private String name;
    }

    private abstract static class ThingResource extends RestlessResourceHandler<Thing, Long> {
        @Override
        public Set<AuthorizationGuard.Action> getEnabledOperations() {
            // Only READ_LIST: resolveMetadata() always resolves the read side regardless, but
            // this keeps every fixture here from also having to stub Create/Update/DeleteDataSource.
            return Set.of(AuthorizationGuard.Action.READ_LIST);
        }

        @Override
        protected Mapper<Thing, ?> getEntityMapper() {
            return source -> null;
        }

        @Override
        protected Mapper<Thing, ?> getOverviewMapper() {
            return getEntityMapper();
        }

        @Override
        protected Mapper<Thing, ?> getSelectMapper() {
            return getEntityMapper();
        }
    }

    private static ThingResource resourceWithSearchDto(ReadDataSource<Thing, Long, ?> readDataSource) {
        return new ThingResource() {
            @Override
            protected ReadDataSource<Thing, Long, ?> getReadDataSource() {
                return readDataSource;
            }
        };
    }

    private static void init(ThingResource resource) {
        resource.init(new RestlessInitContext(resource.resolveMetadata("/things"), new tools.jackson.databind.ObjectMapper(),
                DefaultConversionService.getSharedInstance(), noOpValidator(), RestlessEmbedResolver.NONE, null,
                RestlessAuthorizationMetrics.NONE, 10_000, 2_000, 1_000));
    }

    private static Validator noOpValidator() {
        return new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return true;
            }

            @Override
            public void validate(Object target, Errors errors) {
            }
        };
    }

    @Test
    void aWellFormedSearchDtoInitializesCleanly() {
        init(resourceWithSearchDto(new DefaultReadDataSource<>(null, GoodSearchDto.class)));
    }

    static class EqualityFieldWithNoEntityPropertySearchDto extends AbstractSearchDto {
        private String bogus;
    }

    @Test
    void equalityFieldNamingNoEntityPropertyFailsFast() {
        assertThatThrownBy(() -> init(resourceWithSearchDto(
                new DefaultReadDataSource<>(null, EqualityFieldWithNoEntityPropertySearchDto.class))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bogus");
    }

    static class OperatorTypeMismatchSearchDto extends AbstractSearchDto {
        private List<String> tagsGte; // tags is a List - not Comparable, Gte unsupported.
    }

    @Test
    void operatorTypeMismatchFailsFast() {
        assertThatThrownBy(() -> init(resourceWithSearchDto(
                new DefaultReadDataSource<>(null, OperatorTypeMismatchSearchDto.class))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tagsGte");
    }

    static class InSuffixOnNonCollectionSearchDto extends AbstractSearchDto {
        private String nameIn; // "name" is a String field, not a Collection
    }

    @Test
    void inSuffixOnANonCollectionFieldFailsFast() {
        assertThatThrownBy(() -> init(resourceWithSearchDto(
                new DefaultReadDataSource<>(null, InSuffixOnNonCollectionSearchDto.class))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nameIn")
                .hasMessageContaining("Collection");
    }

    static class PrimitiveFilterFieldSearchDto extends AbstractSearchDto {
        private boolean active;
    }

    @Test
    void primitiveFilterFieldFailsFast() {
        assertThatThrownBy(() -> init(resourceWithSearchDto(
                new DefaultReadDataSource<>(null, PrimitiveFilterFieldSearchDto.class))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active")
                .hasMessageContaining("primitive");
    }

    static class NeitherFullNameNorBaseResolvesSearchDto extends AbstractSearchDto {
        private String ownerIdIn; // neither "ownerIdIn" nor "ownerId" is a Thing property
    }

    @Test
    void suffixedFieldResolvingToNeitherFullNorBaseNameFailsFast() {
        assertThatThrownBy(() -> init(resourceWithSearchDto(
                new DefaultReadDataSource<>(null, NeitherFullNameNorBaseResolvesSearchDto.class))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ownerIdIn")
                .hasMessageContaining("ownerId");
    }

    /** {@code Thing} has no "tags" + "In" ambiguity on its own - add a property that collides. */
    static class ThingWithTagsIn extends Thing {
        private String tagsIn; // full name "tagsIn" is itself a real property here
    }

    private abstract static class TagsInThingResource extends RestlessResourceHandler<ThingWithTagsIn, Long> {
        @Override
        public Set<AuthorizationGuard.Action> getEnabledOperations() {
            return Set.of(AuthorizationGuard.Action.READ_LIST);
        }

        @Override
        protected Mapper<ThingWithTagsIn, ?> getEntityMapper() {
            return source -> null;
        }

        @Override
        protected Mapper<ThingWithTagsIn, ?> getOverviewMapper() {
            return getEntityMapper();
        }

        @Override
        protected Mapper<ThingWithTagsIn, ?> getSelectMapper() {
            return getEntityMapper();
        }
    }

    static class FullNameTakesPrioritySearchDto extends AbstractSearchDto {
        private String tagsIn; // ThingWithTagsIn has a real "tagsIn" String property - this
        // must resolve as plain equality on it, not an "In" operator on a non-existent "tags"
        // Collection property that would otherwise fail the Collection-type check.
    }

    @Test
    void fullFieldNameMatchingAnEntityPropertyTakesPriorityOverTheSuffix() {
        DefaultReadDataSource<ThingWithTagsIn, Long, FullNameTakesPrioritySearchDto> readDataSource =
                new DefaultReadDataSource<>(null, FullNameTakesPrioritySearchDto.class);
        TagsInThingResource resource = new TagsInThingResource() {
            @Override
            protected ReadDataSource<ThingWithTagsIn, Long, ?> getReadDataSource() {
                return readDataSource;
            }
        };
        resource.init(new RestlessInitContext(resource.resolveMetadata("/things-with-tags-in"), new tools.jackson.databind.ObjectMapper(),
                DefaultConversionService.getSharedInstance(), noOpValidator(), RestlessEmbedResolver.NONE, null,
                RestlessAuthorizationMetrics.NONE, 10_000, 2_000, 1_000));
        // No exception: "tagsIn" (the full SearchDto field name) matches ThingWithTagsIn's own
        // "tagsIn" property directly, so it's bound as equality - never evaluated as the "In"
        // suffix against a non-existent, non-Collection "tags" base property.
    }
}
