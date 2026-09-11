package ro.cristivoicu.springbootrestless.example.entity.project;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class ProjectMapper implements Mapper<Project, ProjectDto> {
    @Override
    public ProjectDto map(Project source) {
        return new ProjectDto(source.getId(), source.getName(), source.getDescription());
    }
}
