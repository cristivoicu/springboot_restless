package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code RestlessResourceHandler} subclass for automatic HTTP route registration by
 * {@code RestlessRegistrar}. Lives on the resource-descriptor bean's class — never on the JPA
 * entity itself — so REST concerns stay out of the domain model.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessResource {

    /**
     * The base path this resource is mounted at, e.g. {@code "/employees"}.
     */
    String basePath();
}
