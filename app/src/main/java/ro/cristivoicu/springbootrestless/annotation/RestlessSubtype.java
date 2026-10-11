package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a create model subtype of a polymorphic resource: which entity subclass the default create
 * data source instantiates for it. The usual pairing is a {@code @JsonTypeInfo} create model
 * hierarchy over a {@code SINGLE_TABLE}/{@code JOINED} entity hierarchy:
 * <pre>{@code
 * @RestlessSubtype(SocialGuest.class)
 * public class SocialGuestCreateModel extends GuestCreateModel { ... }
 * }</pre>
 * Without it, {@code DefaultCreateDataSource} instantiates the resource's own entity type - which
 * fails for an abstract one. The subtype must extend the resource's entity type (checked per request,
 * a misconfiguration is a 500 with a clear message, never a row of the wrong kind).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessSubtype {

    Class<?> value();
}
