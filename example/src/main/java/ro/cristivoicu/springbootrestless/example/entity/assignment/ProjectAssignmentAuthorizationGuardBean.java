package ro.cristivoicu.springbootrestless.example.entity.assignment;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.builders.AttributeValue;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosActionNaming;
import ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard;
import ro.cristivoicu.springbootrestless.example.entity.employee.Employee;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeRepository;

import java.util.Map;

/**
 * Same delegating-bean shape as {@code ProjectAuthorizationGuardBean} (a generic {@code
 * CerbosAuthorizationGuard<E>} can't be pointed at directly by {@code
 * @RestlessEntity(authorizationGuard = ...)} - one concrete class per entity needed, see that
 * javadoc), and the same {@code policies/project-assignment.yaml} shape as {@code
 * policies/project.yaml}: admin/manager unconditional, a plain employee read-scoped to their own
 * department - who's on a team is exactly as sensitive as the team's own project list, so
 * membership visibility follows the identical rule. Unlike {@code ProjectAuthorizationGuardBean},
 * the *resource* side needs no extra lookup at all: {@link ProjectAssignment#getDepartmentCode()}
 * is already denormalized onto the row itself (see its javadoc), so {@link
 * ro.cristivoicu.springbootrestless.cerbos.CerbosResourceAttributesMapper} just reads it directly.
 */
@Component
public class ProjectAssignmentAuthorizationGuardBean implements AuthorizationGuard<ProjectAssignment> {

    private static final String DEPARTMENT_CODE_ATTRIBUTE = "departmentCode";

    private final CerbosAuthorizationGuard<ProjectAssignment> delegate;
    private final EmployeeRepository employeeRepository;

    public ProjectAssignmentAuthorizationGuardBean(CerbosBlockingClient cerbosClient, EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
        this.delegate = new CerbosAuthorizationGuard<>(cerbosClient, "project_assignment", ProjectAssignment::getId,
                assignment -> Map.of(DEPARTMENT_CODE_ATTRIBUTE,
                        AttributeValue.stringValue(nullToEmpty(assignment.getDepartmentCode()))),
                CerbosActionNaming.DEFAULT,
                this::ownDepartmentAttribute);
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        return delegate.preCheck(action, customActionName, request);
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, ProjectAssignment entity) {
        return delegate.canAccess(action, request, entity);
    }

    @Override
    public Specification<ProjectAssignment> scope(Action action, String customActionName, HttpServletRequest request) {
        return delegate.scope(action, customActionName, request);
    }

    /** Identical to {@code ProjectAuthorizationGuardBean}'s own - see its javadoc for the full reasoning. */
    private Map<String, AttributeValue> ownDepartmentAttribute(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Map.of();
        }
        String email = jwtAuth.getToken().getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            return Map.of();
        }
        return employeeRepository.findByEmail(email)
                .map(Employee::getDepartmentCode)
                .filter(StringUtils::hasText)
                .map(code -> Map.of(DEPARTMENT_CODE_ATTRIBUTE, AttributeValue.stringValue(code)))
                .orElse(Map.of());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
