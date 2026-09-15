package ro.cristivoicu.springbootrestless.example.entity.project;

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
 * Wraps a {@link CerbosAuthorizationGuard}{@code <Project>} as a named, concrete bean -
 * {@code @RestlessEntity(authorizationGuard = ...)} needs one (see its javadoc: a {@code
 * Class<?>} attribute can't name a parameterized type), so a generic guard implementation meant
 * to back more than one entity can't be pointed at directly. This is the delegating wrapper that
 * limit calls for; {@link Project} is now compile-time generated (no hand-written {@code
 * ProjectRestlessResource}/{@code ProjectRepository}/{@code ProjectMapper} left at all - see its
 * javadoc), and this bean is the one hand-written piece authorization still needs.
 * <p>
 * Backs {@code policies/project.yaml}: admin and manager get {@code "*"} unconditionally;
 * everyone else is read-scoped to {@code departmentCode == <their own department>}. "Their own
 * department" isn't a JWT claim Keycloak issues (unlike {@code policies/employee.yaml}'s {@code
 * scopedLastName}/{@code canViewSalary}) - it's resolved by looking up the authenticated
 * principal's own {@link Employee} row (matched by the JWT's {@code email} claim, via {@link
 * #ownDepartmentAttribute}) and reading its {@code departmentCode}. That needs a lookup {@link
 * CerbosAuthorizationGuard} itself has no way to do, hence {@code principalAttributesExtender}
 * (see its javadoc) rather than a plain {@code CerbosResourceAttributesMapper}, which only ever
 * sees the *resource* side.
 */
@Component
public class ProjectAuthorizationGuardBean implements AuthorizationGuard<Project> {

    private static final String DEPARTMENT_CODE_ATTRIBUTE = "departmentCode";

    private final CerbosAuthorizationGuard<Project> delegate;
    private final EmployeeRepository employeeRepository;

    public ProjectAuthorizationGuardBean(CerbosBlockingClient cerbosClient, EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
        this.delegate = new CerbosAuthorizationGuard<>(cerbosClient, "project", Project::getId,
                project -> Map.of(DEPARTMENT_CODE_ATTRIBUTE,
                        AttributeValue.stringValue(nullToEmpty(project.getDepartmentCode()))),
                CerbosActionNaming.DEFAULT,
                this::ownDepartmentAttribute);
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        return delegate.preCheck(action, customActionName, request);
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, Project entity) {
        return delegate.canAccess(action, request, entity);
    }

    @Override
    public Specification<Project> scope(Action action, String customActionName, HttpServletRequest request) {
        return delegate.scope(action, customActionName, request);
    }

    /**
     * Looks up the authenticated principal's own {@link Employee} row by the JWT {@code email}
     * claim and, if found, contributes its {@code departmentCode} as a Cerbos principal
     * attribute - see this class's javadoc for why that can't just be a JWT claim like {@code
     * scopedLastName} is. Empty (not an error) for a non-JWT principal, a token with no {@code
     * email} claim, or an email with no matching {@link Employee} row - the policy's {@code
     * !has(...) ||} guard treats "no departmentCode attribute" as "don't apply this condition",
     * not as an automatic match.
     */
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
