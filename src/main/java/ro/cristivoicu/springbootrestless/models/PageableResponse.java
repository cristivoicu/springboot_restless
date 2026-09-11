package ro.cristivoicu.springbootrestless.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PageableResponse<B> {
    protected int totalPages;
    protected long totalElements;
    protected int pageSize;
    B body;

    public static <T, K, B> PageableResponse<B> createWithData(Page<T> entities, Function<T, K> mapper) {
        PageableResponse<B> response = new PageableResponse<>();

        response.setPageSize(entities.getSize());
        response.setTotalPages(entities.getTotalPages());
        response.setTotalElements(entities.getTotalElements());

        response.setBody((B) entities.stream()
                .map(mapper)
                .collect(Collectors.toList())
        );

        return response;
    }

    public static <B> PageableResponse<List<B>> createWithData(Page<B> entities) {
        PageableResponse<List<B>> response = new PageableResponse<>();

        response.setPageSize(entities.getSize());
        response.setTotalPages(entities.getTotalPages());
        response.setTotalElements(entities.getTotalElements());

        response.setBody(entities.getContent());

        return response;
    }


}