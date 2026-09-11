package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class DepartmentMapper implements Mapper<Department, DepartmentDto> {
    @Override
    public DepartmentDto map(Department source) {
        return new DepartmentDto(source.getId(), source.getName(), source.getCode());
    }
}
