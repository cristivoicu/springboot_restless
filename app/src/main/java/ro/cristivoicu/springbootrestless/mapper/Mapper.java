package ro.cristivoicu.springbootrestless.mapper;

import java.util.List;

/**
 * Maps an entity to a response DTO. Deliberately has no default, reflection-based
 * implementation — unlike Create/Update/Delete ({@code Default*DataSource}) or the default
 * search filter, which do have reflection-based defaults. A reflective mapper would silently
 * expose whatever fields an entity happens to have; response shaping is the one boundary this
 * framework always keeps hand-written, since it's the surface fine-grained authorization
 * ultimately has to reason about — auto-exposing fields would undermine that goal outright.
 */
public interface Mapper <E, D> {
    D map(E source);
    default List<D> map(List<E> source){
        return source.stream().map(this::map).toList();
    }
}
