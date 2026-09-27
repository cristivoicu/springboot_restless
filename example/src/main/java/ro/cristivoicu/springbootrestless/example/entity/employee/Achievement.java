package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single recorded achievement - same {@code @Embeddable} value-object reasoning as {@link
 * Certification}'s own javadoc. Appended only via the {@code recordAchievement} write action.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {
    private String title;
    private int year;
}
