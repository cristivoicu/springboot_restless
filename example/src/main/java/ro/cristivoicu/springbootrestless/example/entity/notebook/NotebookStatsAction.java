package ro.cristivoicu.springbootrestless.example.entity.notebook;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessActionName;
import ro.cristivoicu.springbootrestless.resource.CollectionAction;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

import java.util.Map;

/** {@code GET /notebooks/actions/stats} - only ever counts the caller's own notebooks. */
@Component
@RestlessActionName("stats")
public class NotebookStatsAction implements CollectionAction<Notebook> {

    private final NotebookRepository repository;

    public NotebookStatsAction(NotebookRepository repository) {
        this.repository = repository;
    }

    @Override
    public Object execute(Specification<Notebook> visibleRows, RestlessContext context) {
        return Map.of("count", repository.count(visibleRows));
    }
}
