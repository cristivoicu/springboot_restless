package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.PatchModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.beans.PropertyDescriptor;
import java.util.Arrays;

/**
 * Default {@link PatchDataSource}: loads the entity by id, copies whichever matching-named
 * properties the {@code PatchModel} declares onto it via {@link BeanUtils#copyProperties} -
 * except any property that's currently {@code null} on the patch DTO itself, which {@link
 * #nullPropertyNames} excludes from the copy so an unset field is left alone rather than
 * overwritten with {@code null} (see {@link PatchDataSource}'s javadoc for what that does and
 * doesn't let a client express).
 */
public class DefaultPatchDataSource<E, K, P extends PatchModel>
        extends PatchDataSource<E, K, P> implements TypedDataSource<P> {

    private final Class<P> dtoType;

    public DefaultPatchDataSource(SpecificationRepository<E, K> repository, Class<P> dtoType) {
        super(repository);
        this.dtoType = dtoType;
    }

    @Override
    public E patch(K id, P patchDto) {
        E entity = specificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + id + " not found"));
        BeanUtils.copyProperties(patchDto, entity, ignoredPropertyNames(patchDto, entity.getClass()));
        return specificationRepository.save(entity);
    }

    @Override
    public Class<P> getDtoType() {
        return dtoType;
    }

    private static String[] nullPropertyNames(Object source) {
        BeanWrapper wrapper = new BeanWrapperImpl(source);
        return Arrays.stream(wrapper.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> wrapper.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    /** {@link #nullPropertyNames} unioned with {@link ProtectedEntityFields#of} - see that class's own javadoc for why a patch DTO's field names need the same shielding a create/update DTO's do. */
    private static String[] ignoredPropertyNames(Object source, Class<?> entityType) {
        java.util.LinkedHashSet<String> ignored = new java.util.LinkedHashSet<>(Arrays.asList(nullPropertyNames(source)));
        ignored.addAll(Arrays.asList(ProtectedEntityFields.of(entityType)));
        return ignored.toArray(new String[0]);
    }
}
