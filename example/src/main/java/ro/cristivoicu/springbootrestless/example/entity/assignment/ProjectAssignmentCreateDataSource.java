package ro.cristivoicu.springbootrestless.example.entity.assignment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.example.entity.employee.Employee;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeRepository;
import ro.cristivoicu.springbootrestless.example.entity.project.Project;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link #create} (single item) validates and saves one row. {@link #createAll} (bulk) is
 * deliberately <em>not</em> the inherited default (a loop of {@link #create} calls, one query per
 * validation per item) - at the scale this resource exists for, that's O(n) round trips for a
 * batch that's usually scoped to a handful of distinct projects: this overrides it to validate
 * the whole batch with three queries total (projects referenced, employees referenced, existing
 * assignments for those projects) and a single {@code saveAll()}, regardless of whether the batch
 * has 10 items or 10,000. See {@link ProjectAssignment}'s javadoc for the {@code
 * GenerationType.SEQUENCE}/{@code batch_size} pairing that lets that {@code saveAll()} actually
 * batch at the JDBC level too.
 */
@Component
public class ProjectAssignmentCreateDataSource extends CreateDataSource<ProjectAssignment, Long, ProjectAssignmentCreateModel> {

    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;

    public ProjectAssignmentCreateDataSource(ProjectAssignmentRepository assignmentRepository,
                                              ProjectRepository projectRepository,
                                              EmployeeRepository employeeRepository) {
        super(assignmentRepository);
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public ProjectAssignment create(ProjectAssignmentCreateModel createDto) {
        Project project = projectRepository.findById(createDto.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No project with id " + createDto.getProjectId()));
        if (!employeeRepository.existsById(createDto.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No employee with id " + createDto.getEmployeeId());
        }
        if (assignmentRepository.existsByProjectIdAndEmployeeId(createDto.getProjectId(), createDto.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee " + createDto.getEmployeeId()
                    + " is already assigned to project " + createDto.getProjectId());
        }

        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setProjectId(project.getId());
        assignment.setEmployeeId(createDto.getEmployeeId());
        assignment.setDepartmentCode(project.getDepartmentCode());
        return specificationRepository.save(assignment);
    }

    /**
     * All-or-nothing (the caller's {@code @Transactional POST .../bulk} takes care of the actual
     * rollback - see {@code RestlessResourceHandler#createBulk}): every problem in the batch is
     * collected before anything is saved, so a 400 here always means nothing was written, and the
     * caller finds out about every bad row in one response instead of one failed request per bad
     * row.
     */
    @Override
    public List<ProjectAssignment> createAll(List<ProjectAssignmentCreateModel> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        Set<Long> projectIds = items.stream().map(ProjectAssignmentCreateModel::getProjectId).collect(Collectors.toSet());
        Set<Long> employeeIds = items.stream().map(ProjectAssignmentCreateModel::getEmployeeId).collect(Collectors.toSet());

        Map<Long, Project> projectsById = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, project -> project));
        Set<Long> existingEmployeeIds = employeeRepository.findAllById(employeeIds).stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());
        Set<String> alreadyAssigned = assignmentRepository.findByProjectIdIn(projectIds).stream()
                .map(a -> pairKey(a.getProjectId(), a.getEmployeeId()))
                .collect(Collectors.toCollection(HashSet::new));

        List<String> problems = new ArrayList<>();
        Set<String> seenInThisBatch = new HashSet<>();
        List<ProjectAssignment> toSave = new ArrayList<>(items.size());

        for (ProjectAssignmentCreateModel item : items) {
            Project project = projectsById.get(item.getProjectId());
            if (project == null) {
                problems.add("no project with id " + item.getProjectId());
                continue;
            }
            if (!existingEmployeeIds.contains(item.getEmployeeId())) {
                problems.add("no employee with id " + item.getEmployeeId());
                continue;
            }
            String key = pairKey(item.getProjectId(), item.getEmployeeId());
            if (alreadyAssigned.contains(key) || !seenInThisBatch.add(key)) {
                problems.add("duplicate assignment (projectId=" + item.getProjectId()
                        + ", employeeId=" + item.getEmployeeId() + ")");
                continue;
            }

            ProjectAssignment assignment = new ProjectAssignment();
            assignment.setProjectId(item.getProjectId());
            assignment.setEmployeeId(item.getEmployeeId());
            assignment.setDepartmentCode(project.getDepartmentCode());
            toSave.add(assignment);
        }

        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk assignment rejected: " + String.join("; ", problems));
        }
        return specificationRepository.saveAll(toSave);
    }

    private static String pairKey(Long projectId, Long employeeId) {
        return projectId + ":" + employeeId;
    }
}
