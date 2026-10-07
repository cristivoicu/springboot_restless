package ro.cristivoicu.springbootrestless.example.entity.task;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectRepository;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

/**
 * Deliberately not Cerbos-backed - this example's point is {@code RestlessContext}, not another
 * policy-as-code demo (see {@link ro.cristivoicu.springbootrestless.example.entity.project.ProjectAuthorizationGuardBean}
 * for that). Demonstrates Feature B item 8 ("a guard should be able to cheaply authorize the
 * parent once per request via {@code RestlessContext} in {@code preCheck}"): resolves the
 * {@code projectId} path variable once and rejects the request outright (before any {@code Task}
 * row is even loaded) if that project doesn't exist - cheaper than letting every action fall
 * through to {@code RestlessResourceHandler}'s own per-row 404-on-parent-mismatch check, which
 * only ever runs once a row has already been loaded.
 * <p>
 * Every other hook ({@code canAccess}/{@code scope}/...) stays at {@link AuthorizationGuard}'s own
 * permissive default - this guard exists to prove the one new capability, not to re-demonstrate
 * row-level authorization already covered exhaustively elsewhere in this example app.
 */
@Component
public class TaskAuthorizationGuardBean implements AuthorizationGuard<Task> {

    private final ProjectRepository projectRepository;

    public TaskAuthorizationGuardBean(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        Long projectId = RestlessContext.current().pathVariable("projectId", Long.class);
        return projectId != null && projectRepository.existsById(projectId);
    }
}
