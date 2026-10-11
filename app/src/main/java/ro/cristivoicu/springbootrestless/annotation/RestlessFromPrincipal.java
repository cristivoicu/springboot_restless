package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A server-set field: on create it's written from the current principal's id (see {@code
 * RestlessPrincipalIdResolver}), converted to the field's type - never read from the request body -
 * and afterwards it's immutable like {@code @Id}.
 * <p>
 * The typical use is the owner column of a user-owned row ({@code ownerId}, {@code createdBy}).
 * <ul>
 *   <li>Create with no resolvable principal is a {@code 401}, never a row with a {@code null} owner.</li>
 *   <li>Update/patch can't change it: whatever a data source does, the stored value is restored
 *       before the transaction commits.</li>
 *   <li>The processor rejects a create/update/patch model that declares a same-named field - a
 *       client value for it would only ever be ignored, which is a mistake worth catching early.</li>
 * </ul>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessFromPrincipal {
}
