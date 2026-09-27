package ro.cristivoicu.springbootrestless.example.entity.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

import java.math.BigDecimal;

@Getter
@Setter
public class EmployeeSearchDto extends AbstractSearchDto {

    @Schema(description = "Exact-match filter on last name.", example = "Lovelace")
    private String lastName;

    /**
     * Filter-DSL demo via {@code EmployeeRestlessResource}'s own hand-written {@code
     * getSpecification()} override (using {@code RestlessSpecifications}), not the reflection-
     * driven default - {@code Employee} already needs a hand-written override for {@code
     * lastName}'s masking-aware guard interaction, so these two fields ride along on that same
     * override rather than the automatic suffix mechanism {@code ProjectSearchDto} demonstrates.
     */
    @Schema(description = "Only employees earning at least this much.", example = "80000.00")
    private BigDecimal salaryGte;

    @Schema(description = "Only employees earning at most this much.", example = "150000.00")
    private BigDecimal salaryLte;
}
