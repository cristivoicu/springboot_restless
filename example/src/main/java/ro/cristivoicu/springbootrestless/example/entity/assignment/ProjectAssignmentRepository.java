package ro.cristivoicu.springbootrestless.example.entity.assignment;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProjectAssignmentRepository extends SpecificationRepository<ProjectAssignment, Long> {

    boolean existsByProjectIdAndEmployeeId(Long projectId, Long employeeId);

    /**
     * Used by {@link ProjectAssignmentCreateDataSource#createAll} to duplicate-check an entire
     * batch in one query instead of one {@code existsBy...} call per item: a bulk add is almost
     * always scoped to a handful of distinct projects (usually just one) even when it's adding
     * thousands of employees to them, so this stays cheap regardless of batch size.
     */
    List<ProjectAssignment> findByProjectIdIn(Collection<Long> projectIds);
}
