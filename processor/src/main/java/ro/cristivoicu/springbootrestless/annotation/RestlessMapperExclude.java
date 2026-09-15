package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Placed on a DTO field (not the entity's own field) to keep it out of the reflective, default
 * {@code Mapper} {@code RestlessEntityProcessor} generates when an entity has no hand-written
 * {@code {Entity}Mapper} of its own (see {@link RestlessEntity#mapper()}'s javadoc) — that
 * generated mapper otherwise does a plain {@code BeanUtils.copyProperties(entity, dto)}, matching
 * fields by name; a field annotated here is added to that call's {@code ignoreProperties}, so it's
 * always left at its Java default (usually {@code null}) instead of being auto-populated.
 * <p>
 * <b>Not the same thing as {@code @CerbosHiddenField}.</b> That one (in the optional {@code
 * cerbos} module) is a runtime, per-request, per-principal decision — masked for this caller only,
 * based on a live policy check. This one is a compile-time, unconditional decision baked into the
 * generated mapper itself — the field is never populated by it at all, for anyone, full stop. Use
 * this for a field a generated mapper genuinely has no business copying blindly (a computed value,
 * a field meant to be set by other code after mapping, ...); reach for a hand-written {@code
 * Mapper} entirely — not this annotation — for anything that needs actual per-caller access
 * control, {@code @CerbosHiddenField} included.
 * <p>
 * Has no effect at all once a hand-written {@code {Entity}Mapper} exists — generation (and
 * therefore this annotation) only applies when there isn't one.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessMapperExclude {
}
