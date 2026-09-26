package ro.cristivoicu.springbootrestless.example.entity.department;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;

/**
 * This app's one demo of {@link SoftDeletable} (see {@code DepartmentRestlessResource}'s {@code
 * DefaultSoftDeleteDataSource} wiring) - archiving a department flags it rather than removing the
 * row outright, so {@code Employee}/{@code Project} rows still referencing its {@code code} (a
 * natural key, not a JPA {@code @ManyToOne} - this codebase keeps every entity flat) never point
 * at a hard-deleted record. Lombok's {@code @Getter}/{@code @Setter} on the primitive {@code
 * boolean deleted} field already generate {@code isDeleted()}/{@code setDeleted(boolean)} -
 * exactly {@link SoftDeletable}'s two methods, no hand-written overrides needed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Department implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String code;

    private boolean deleted;
}
