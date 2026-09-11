package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;

@Component
public class DepartmentUpdateDataSource extends UpdateDataSource<Department, Long, DepartmentUpdateModel> {

    public DepartmentUpdateDataSource(DepartmentRepository repository) {
        super(repository);
    }

    @Override
    public Department update(Long id, DepartmentUpdateModel updateDto) {
        Department department = specificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department " + id + " not found"));
        department.setName(updateDto.getName());
        department.setCode(updateDto.getCode());
        return specificationRepository.save(department);
    }
}
