package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single earned certification - {@code @Embeddable}, not its own {@code @Entity}/REST resource:
 * this codebase keeps every entity flat (no {@code @ManyToOne}/{@code @OneToMany} associations
 * between aggregates anywhere, see {@link Employee}'s own javadoc), and a certification has no
 * identity or lifecycle independent of the employee who earned it - a genuine value object, not a
 * second aggregate. Appended only via the {@code addCertification} write action ({@link
 * EmployeeRestlessResource#getCustomWriteActions()}) - deliberately absent from {@link
 * EmployeeUpdateModel}, so a full-replace {@code PUT} can't silently drop or rewrite earned
 * certifications; the only way to add one is the command that means exactly that.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Certification {
    private String name;
    private int yearEarned;
}
