package ro.cristivoicu.springbootrestless.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PageableResponse<B> {
    protected int totalPages;
    protected long totalElements;
    protected int pageSize;
    B body;
}
