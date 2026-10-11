package ro.cristivoicu.springbootrestless.example.entity.notebook;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessActionName;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;
import ro.cristivoicu.springbootrestless.resource.WriteAction;

/** {@code POST /notebooks/{id}/actions/stamp}: appends a mark to the title - a write action on the generated tier. */
@Component
@RestlessActionName("stamp")
public class NotebookStampAction implements WriteAction<Notebook, NotebookStampAction.Request, NotebookDto> {

    public static class Request implements WriteActionRequest {
    }

    private final NotebookMapper mapper;

    public NotebookStampAction(NotebookMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Class<Request> getRequestType() {
        return Request.class;
    }

    @Override
    public Class<NotebookDto> getResponseType() {
        return NotebookDto.class;
    }

    @Override
    public NotebookDto execute(Notebook notebook, Request request) {
        notebook.setTitle(notebook.getTitle() + " ✓");
        return mapper.map(notebook);
    }
}
