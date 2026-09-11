package ro.cristivoicu.springbootrestless.mapper;

import java.util.List;

public interface Mapper <E, D> {
    D map(E source);
    default List<D> map(List<E> source){
        return source.stream().map(this::map).toList();
    }
}
