package ro.cristivoicu.springbootrestless.example.entity.notebook;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;
import ro.cristivoicu.springbootrestless.resource.RestlessLifecycleHook;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every notebook write it sees - a stand-in for "publish an event" or "delete the files". */
@Component
public class NotebookAuditHook implements RestlessLifecycleHook<Notebook> {

    private final List<String> entries = new CopyOnWriteArrayList<>();

    public List<String> entries() {
        return entries;
    }

    @Override
    public void afterCreate(Notebook created, RestlessContext context) {
        entries.add("created " + created.getId() + " by " + created.getOwnerUsername());
    }

    @Override
    public void afterUpdate(Notebook updated, RestlessContext context) {
        entries.add("updated " + updated.getId());
    }

    @Override
    public void beforeDelete(Notebook existing, RestlessContext context) {
        entries.add("deleting " + existing.getId() + " titled " + existing.getTitle());
    }
}
