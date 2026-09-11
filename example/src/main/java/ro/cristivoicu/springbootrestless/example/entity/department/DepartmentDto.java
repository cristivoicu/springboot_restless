package ro.cristivoicu.springbootrestless.example.entity.department;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto implements EntityDto {
    private Long id;
    private String name;
    private String code;
}
