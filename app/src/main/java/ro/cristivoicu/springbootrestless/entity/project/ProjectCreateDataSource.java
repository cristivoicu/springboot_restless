package ro.cristivoicu.springbootrestless.entity.project;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

/**
 * Escape-hatch proof: referenced via {@code @RestlessEntity(createDataSource =
 * ProjectCreateDataSource.class)} on {@link Project}, so the generated {@code
 * ProjectRestlessResource} injects this hand-written bean instead of generating a {@code
 * DefaultCreateDataSource} call — something the default (a plain field-by-field copy) can't
 * express: defaulting a blank description instead of leaving it blank.
 */
@Component
public class ProjectCreateDataSource extends CreateDataSource<Project, Long, ProjectCreateModel> {

    private static final String DEFAULT_DESCRIPTION = "No description provided";

    protected ProjectCreateDataSource(ProjectRepository repository) {
        super(repository);
    }

    @Override
    public Project create(ProjectCreateModel createDto) {
        Project project = new Project();
        project.setName(createDto.getName());
        project.setDescription(createDto.getDescription() == null || createDto.getDescription().isBlank()
                ? DEFAULT_DESCRIPTION
                : createDto.getDescription());
        return specificationRepository.save(project);
    }
}
