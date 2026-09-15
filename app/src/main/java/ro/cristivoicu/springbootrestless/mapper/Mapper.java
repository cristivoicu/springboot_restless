package ro.cristivoicu.springbootrestless.mapper;

import java.util.List;

/**
 * Maps an entity to a response DTO. This interface itself has no default, reflection-based
 * implementation — unlike {@code Default*DataSource} or the default search filter — because a
 * reflective mapper that also invents its own DTO shape would silently expose whatever fields an
 * entity happens to have; response *shape* is the one boundary this framework always keeps
 * hand-written, since it's the surface fine-grained authorization ultimately has to reason about.
 * <p>
 * That's a statement about the DTO's shape, not about who writes the field-copying code once that
 * shape is already decided: {@code RestlessEntityProcessor} (see {@code processor} module) can
 * generate a reflective implementation of this interface — {@code BeanUtils.copyProperties} onto
 * an already-hand-written DTO — when an entity has no hand-written {@code {Entity}Mapper} of its
 * own. The DTO's fields are still always authored by hand; only the mechanical "copy this
 * entity's fields onto that already-declared DTO's matching fields" step is optional. See {@code
 * ro.cristivoicu.springbootrestless.annotation.RestlessEntity#mapper()} and {@code
 * RestlessMapperExclude} in the {@code processor} module.
 */
public interface Mapper <E, D> {
    D map(E source);
    default List<D> map(List<E> source){
        return source.stream().map(this::map).toList();
    }
}
