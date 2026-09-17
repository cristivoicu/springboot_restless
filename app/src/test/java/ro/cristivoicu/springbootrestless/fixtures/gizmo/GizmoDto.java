package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbed;
import ro.cristivoicu.springbootrestless.fixtures.gadget.Gadget;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetDto;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetRestlessResource;
import ro.cristivoicu.springbootrestless.models.EntityDto;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GizmoDto implements EntityDto {
    private Long id;
    private String name;
    private String code;

    /**
     * Test-only proof that {@code @RestlessEmbed} needs no Cerbos at all: {@link
     * GadgetRestlessResource}'s own header-based {@code AuthorizationGuard} (not a Cerbos one)
     * still gets consulted through {@code findEmbeddedList} exactly as it would on {@code
     * /gadgets-dynamic} itself - see {@code GizmoEmbedTest}. Joined on {@link Gizmo#getName()} ==
     * {@link Gadget#getLastName()}, a deliberately arbitrary pairing (no real-world meaning) - only
     * the mechanism is under test here.
     */
    @RestlessEmbed(resource = GadgetRestlessResource.class, sourceField = "name", targetField = "lastName", many = true)
    private List<GadgetDto> gadgets;
}
