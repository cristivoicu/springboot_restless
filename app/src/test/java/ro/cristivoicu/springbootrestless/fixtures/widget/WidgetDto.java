package ro.cristivoicu.springbootrestless.fixtures.widget;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/** {@code version} is exposed so a client can read it back and send it as {@code If-Match} on its next write - the realistic usage this fixture is proving. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WidgetDto implements EntityDto {
    private Long id;
    private String name;
    private Long version;
}
