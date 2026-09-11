package ro.cristivoicu.springbootrestless.models;

import java.util.List;

/**
 * Marks a POJO as a bulk-delete model for an entity. Ids are strings, converted to the
 * entity's actual key type via {@code ConversionService} — the same mechanism used for
 * path-variable ids — rather than making JSON deserialization guess the key type's shape.
 */
public interface DeleteModel {
    List<String> getIds();
}
