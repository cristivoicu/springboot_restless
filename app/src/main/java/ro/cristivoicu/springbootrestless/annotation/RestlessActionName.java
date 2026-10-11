package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The route name of a read action, collection action, write action or named view bean listed on
 * {@code @RestlessEntity} - the last path segment it's served at ({@code .../actions/summary},
 * {@code .../{id}/chart.pdf}). Required on every such bean; a missing or duplicate name is a
 * startup error.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessActionName {

    String value();
}
