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
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosActionNaming;
import ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.example.entity.employee.Employee;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeRepository;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Map;

/**
 * Manual resource (see {@link Project}'s javadoc for why): default read/update/delete, the same
 * blank-description-defaulting {@link ProjectCreateDataSource} the generated resource used to
 * inject, and a real {@link CerbosAuthorizationGuard} backing {@code policies/project.yaml} -
 * admin/manager unrestricted, everyone else scoped to their own department.
 * <p>
 * "Their own department" isn't a JWT claim Keycloak issues (unlike {@code
 * policies/employee.yaml}'s {@code scopedLastName}/{@code canViewSalary}) - it's resolved by
 * looking up the authenticated principal's own {@link Employee} row (matched by the JWT's {@code
 * email} claim, via {@link #ownDepartmentAttribute}) and reading its {@code departmentCode}. That
 * needs a lookup {@link CerbosAuthorizationGuard} itself has no way to do, hence {@code
 * principalAttributesExtender} (see its javadoc) rather than a plain {@code
 * CerbosResourceAttributesMapper}, which only ever sees the *resource* side.
 */
@Component
@RestlessResource(basePath = "/projects")
public class ProjectRestlessResource extends RestlessResourceHandler<Project, Long> {

    private static final String DEPARTMENT_CODE_ATTRIBUTE = "departmentCode";

    private final ProjectCreateDataSource createDataSource;
    private final ReadDataSource<Project, Long, ProjectSearchDto> readDataSource;
    private final UpdateDataSource<Project, Long, ProjectUpdateModel> updateDataSource;
    private final DeleteDataSource<Project, Long, ?> deleteDataSource;
    private final ProjectMapper mapper;
    private final CerbosBlockingClient cerbosClient;
    private final EmployeeRepository employeeRepository;

    public ProjectRestlessResource(ProjectRepository repository, ProjectCreateDataSource createDataSource,
                                    ProjectMapper mapper, CerbosBlockingClient cerbosClient,
                                    EmployeeRepository employeeRepository) {
        this.createDataSource = createDataSource;
        this.readDataSource = new DefaultReadDataSource<>(repository, ProjectSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, ProjectUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
        this.cerbosClient = cerbosClient;
        this.employeeRepository = employeeRepository;
    }

    @Override
    protected CreateDataSource<Project, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Project, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Project, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Project, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Project, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Project, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Project, ?> getSelectMapper() {
        return mapper;
    }

    // getSpecification() intentionally not overridden: RestlessResourceHandler's default
    // (equality-match on populated ProjectSearchDto fields - name, departmentCode) is enough;
    // row-level restriction on top of that is the guard's job (scope()), not the search filter's.

    /**
     * {@code policies/project.yaml}: admin and manager get {@code "*"} unconditionally; everyone
     * else is read-scoped to {@code departmentCode == <their own department>}.
     */
    @Override
    protected AuthorizationGuard<Project> getAuthorizationGuard() {
        return new CerbosAuthorizationGuard<>(cerbosClient, "project", Project::getId,
                project -> Map.of(DEPARTMENT_CODE_ATTRIBUTE,
                        AttributeValue.stringValue(nullToEmpty(project.getDepartmentCode()))),
                CerbosActionNaming.DEFAULT,
                this::ownDepartmentAttribute);
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
