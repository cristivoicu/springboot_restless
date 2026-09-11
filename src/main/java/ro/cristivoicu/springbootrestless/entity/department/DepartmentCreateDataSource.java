package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

@Component
public class DepartmentCreateDataSource extends CreateDataSource<Department, Long, DepartmentCreateModel> {

    public DepartmentCreateDataSource(DepartmentRepository repository) {
        super(repository);
    }

    @Override
    public Department create(DepartmentCreateModel createDto) {
        Department department = new Department();
        department.setName(createDto.getName());
        department.setCode(createDto.getCode());
        return specificationRepository.save(department);
    }
}
