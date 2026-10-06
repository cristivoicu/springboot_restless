package ro.cristivoicu.springbootrestless.processor;

import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessOperation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@code {Entity}RestlessResource} class (the same shape a human would hand-write —
 * see {@code DepartmentRestlessResource} in the app module) for every class annotated {@link
 * RestlessEntity}. Sibling DTOs/{@code Mapper} are found by naming convention ({@code
 * {Entity}CreateModel} etc. in the entity's own package) unless overridden via the annotation's
 * attributes; a missing {@code {Entity}Repository} is generated too. Registered via
 * {@code META-INF/services/javax.annotation.processing.Processor}.
 * <p>
 * Declared {@code isolating} (Ground rules Phase 3 item 15), not {@code aggregating}, in {@code
 * META-INF/gradle/incremental.annotation.processors}: {@link #generate} only ever reads the one
 * {@code @RestlessEntity}-annotated class it was invoked for (plus whichever sibling DTO/{@code
 * Mapper}/{@code *DataSource} types its own attributes name) and writes only that one entity's
 * generated sources - one entity's annotation never affects another's output, the exact
 * precondition {@code isolating} requires. A Gradle incremental build can therefore safely
 * reprocess only the entities whose own source files actually changed.
 */
@SupportedAnnotationTypes("ro.cristivoicu.springbootrestless.annotation.RestlessEntity")
public class RestlessEntityProcessor extends AbstractProcessor {

    private static final String ID_ANNOTATION = "jakarta.persistence.Id";
    private static final String VERSION_ANNOTATION = "jakarta.persistence.Version";
    private static final String CREATED_DATE_ANNOTATION = "org.springframework.data.annotation.CreatedDate";
    private static final String LAST_MODIFIED_DATE_ANNOTATION = "org.springframework.data.annotation.LastModifiedDate";
    private static final String SOFT_DELETABLE_INTERFACE = "ro.cristivoicu.springbootrestless.datasource.SoftDeletable";
    private static final String VOID_SENTINEL = "java.lang.Void"; // "not overridden" - see RestlessEntity's javadoc
    private static final String MAPPER_EXCLUDE_ANNOTATION = "ro.cristivoicu.springbootrestless.annotation.RestlessMapperExclude";

    /** A resolved {@code *DataSource} verb: either "use the default" (typeFqn == null,
     * constructed inline from the repository) or "inject this hand-written bean instead"
     * (typeFqn set, added as a constructor parameter). */
    private record VerbOverride(String typeFqn) {
        boolean isOverridden() {
            return typeFqn != null;
        }
    }

    private Elements elements;
    private Messager messager;
    private Filer filer;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.types = processingEnv.getTypeUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(RestlessEntity.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@RestlessEntity can only be placed on a class", element);
                continue;
            }
            try {
                generate((TypeElement) element);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate resource class: " + e.getMessage(), element);
            }
        }
        return true;
    }

    private void generate(TypeElement entityType) throws IOException {
        String entityName = entityType.getSimpleName().toString();
        String packageName = elements.getPackageOf(entityType).getQualifiedName().toString();

        String idType = resolveIdType(entityType);
        if (idType == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@RestlessEntity: " + entityName + " has no field annotated @jakarta.persistence.Id", entityType);
            return;
        }

        RestlessEntity restlessEntity = entityType.getAnnotation(RestlessEntity.class);
        Set<RestlessOperation> operationSet = Set.of(restlessEntity.operations());
        boolean createEnabled = operationSet.contains(RestlessOperation.CREATE);
        boolean updateEnabled = operationSet.contains(RestlessOperation.UPDATE);

        // createModel/updateModel only need to resolve (convention or override) when their verb
        // is actually enabled - excluding UPDATE from operations() is exactly what lets an entity
        // with no {Entity}UpdateModel (e.g. a link/bridge row nothing ever edits in place) still
        // use the generated tier, instead of needing a phantom DTO class no route ever reaches.
        String createModel = createEnabled
                ? resolveDtoType(entityType, "createModel", packageName, entityName, "CreateModel")
                : null;
        String updateModel = updateEnabled
                ? resolveDtoType(entityType, "updateModel", packageName, entityName, "UpdateModel")
                : null;
        String searchDto = resolveDtoType(entityType, "searchDto", packageName, entityName, "SearchDto");
        if ((createEnabled && createModel == null) || (updateEnabled && updateModel == null) || searchDto == null) {
            return; // errors already reported via the messager
        }

        // Mass-assignment hardening (Ground rules item 6): Default{Create,Update}DataSource's
        // BeanUtils.copyProperties would otherwise happily copy a client-controlled id/version/
        // deleted/audit-timestamp value straight onto the entity (see ProtectedEntityFields in
        // "app") - catching it here, at compile time, means the hole never exists in the first
        // place for anything going through the naming-convention/generated tier.
        Set<String> reservedFieldNames = reservedEntityFieldNames(entityType);
        if (createEnabled) {
            checkNoProtectedFields(createModel, reservedFieldNames, "CreateModel", entityType);
        }
        if (updateEnabled) {
            checkNoProtectedFields(updateModel, reservedFieldNames, "UpdateModel", entityType);
        }

        String mapper = resolveMapperType(entityType, packageName, entityName);
        if (mapper == null) {
            return; // errors already reported via the messager
        }

        String repository = resolveOrGenerateRepository(packageName, entityName, idType, entityType);

        VerbOverride createDataSource = resolveVerbOverride(entityType, "createDataSource");
        VerbOverride readDataSource = resolveVerbOverride(entityType, "readDataSource");
        VerbOverride updateDataSource = resolveVerbOverride(entityType, "updateDataSource");
        VerbOverride deleteDataSource = resolveVerbOverride(entityType, "deleteDataSource");
        VerbOverride authorizationGuard = resolveVerbOverride(entityType, "authorizationGuard");
        VerbOverride patchDataSource = resolveVerbOverride(entityType, "patchDataSource");

        // Same mass-assignment/primitive-field hardening as createModel/updateModel above, for
        // whichever PatchModel the patchDataSource override resolves to (best-effort: only when
        // its direct supertype is a parameterized Default*/```PatchDataSource<E, K, P>``` -
        // the shape every example in RestlessEntity's own javadoc uses).
        TypeElement patchModelElement = resolvePatchModelType(patchDataSource);
        if (patchModelElement != null) {
            checkNoProtectedFields(patchModelElement.getQualifiedName().toString(), reservedFieldNames, "PatchModel", entityType);
            checkPatchModelHasNoPrimitiveFields(patchModelElement);
        }

        writeResourceClass(packageName, entityName, idType, createModel, updateModel, searchDto, mapper,
                repository, restlessEntity.basePath(), restlessEntity.version(), restlessEntity.operations(),
                restlessEntity.allowAll(), createEnabled, updateEnabled, createDataSource, readDataSource,
                updateDataSource, deleteDataSource, authorizationGuard, patchDataSource, entityType);
    }

    /**
     * The annotation's own {@code mapper} override if set, otherwise a hand-written {@code
     * {Entity}Mapper} naming-convention sibling if one exists - both unchanged from before. New:
     * if neither exists, generates a reflective default (see {@link #generateDefaultMapper}) onto
     * a hand-written {@code {Entity}Dto} (its own override/convention resolution, via {@link
     * #resolveDtoType}) instead of erroring - {@code Mapper} itself still has no default because
     * *shape* stays hand-written; only the field-by-field copy of an already-declared shape does.
     */
    private String resolveMapperType(TypeElement entityType, String packageName, String entityName) throws IOException {
        TypeMirror override = readClassAttribute(entityType, "mapper");
        if (!isVoidSentinel(override)) {
            return override.toString();
        }
        String conventionQualifiedName = packageName + "." + entityName + "Mapper";
        if (elements.getTypeElement(conventionQualifiedName) != null) {
            return conventionQualifiedName;
        }

        String dtoType = resolveDtoType(entityType, "dto", packageName, entityName, "Dto");
        if (dtoType == null) {
            return null; // resolveDtoType already reported an error via the messager
        }
        return generateDefaultMapper(packageName, entityName, dtoType, entityType);
    }

    /** One field this processor can see on both sides of a generated mapper - name plus the declared Java type, for matching by name and checking assignability. */
    private record MapperField(String name, TypeMirror type) {
    }

    /**
     * Every non-static field on {@code type}'s own class hierarchy (up to, not including {@code
     * Object}) - same walk-the-superclasses idiom {@link #reservedEntityFieldNames} already uses,
     * since either side of a generated mapper (entity or DTO) can carry a field on a shared
     * {@code @MappedSuperclass} rather than its own concrete class.
     */
    private List<MapperField> declaredFieldsOf(TypeElement type) {
        List<MapperField> fields = new ArrayList<>();
        for (TypeElement current = type; current != null && !current.getQualifiedName().contentEquals("java.lang.Object");
             current = asTypeElement(current.getSuperclass())) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD && !enclosed.getModifiers().contains(Modifier.STATIC)) {
                    fields.add(new MapperField(enclosed.getSimpleName().toString(), enclosed.asType()));
                }
            }
        }
        return fields;
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Every field {@link RestlessMapperExclude} marks on {@code dtoQualifiedName} - resolved by
     * inspecting the DTO's own declared fields, the same {@code getEnclosedElements()} technique
     * {@link #resolveIdType} already uses for {@code @Id}. Empty (not an error) if the type can't
     * be resolved as an element (shouldn't happen - {@link #resolveDtoType} already verified it
     * exists) or declares no excluded fields at all.
     */
    private List<String> resolveExcludedMapperFields(String dtoQualifiedName) {
        TypeElement dtoElement = elements.getTypeElement(dtoQualifiedName);
        if (dtoElement == null) {
            return List.of();
        }
        List<String> excluded = new ArrayList<>();
        for (Element enclosed : dtoElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            for (AnnotationMirror mirror : enclosed.getAnnotationMirrors()) {
                if (mirror.getAnnotationType().toString().equals(MAPPER_EXCLUDE_ANNOTATION)) {
                    excluded.add(enclosed.getSimpleName().toString());
                }
            }
        }
        return excluded;
    }

    /**
     * Generates a {@code {Entity}Mapper implements Mapper<Entity, Dto>} whose {@code map(...)} is
     * one explicit {@code dto.setX(source.getX())} call per matched field pair (Ground rules
     * Phase 3 item 15) - compile-time type-checked, no reflection, a step toward GraalVM
     * native-image support (reflective {@code BeanUtils.copyProperties} needs runtime hints
     * native-image can't infer on its own). A DTO field matches when {@code entityType} declares
     * a same-named field whose type is assignable to it (same leniency {@code
     * BeanUtils.copyProperties} already had via its own {@code PropertyDescriptor} type check -
     * silently skip, don't fail the build, on a name collision between two unrelated types) and
     * it isn't {@literal @}RestlessMapperExclude-annotated. A DTO field present on one side but
     * not the other keeps today's behavior: silently not copied.
     */
    private String generateDefaultMapper(String packageName, String entityName, String dtoType, TypeElement entityType) throws IOException {
        String simpleName = entityName + "Mapper";
        String qualifiedName = packageName + "." + simpleName;

        List<String> excluded = resolveExcludedMapperFields(dtoType);
        TypeElement dtoElement = elements.getTypeElement(dtoType);
        List<MapperField> entityFields = declaredFieldsOf(entityType);

        StringBuilder assignments = new StringBuilder();
        if (dtoElement != null) {
            for (MapperField dtoField : declaredFieldsOf(dtoElement)) {
                if (excluded.contains(dtoField.name())) {
                    continue;
                }
                MapperField entityField = entityFields.stream()
                        .filter(f -> f.name().equals(dtoField.name())).findFirst().orElse(null);
                if (entityField == null || !types.isAssignable(entityField.type(), dtoField.type())) {
                    continue;
                }
                String capitalized = capitalize(dtoField.name());
                String getter = entityField.type().getKind() == TypeKind.BOOLEAN ? "is" + capitalized : "get" + capitalized;
                assignments.append("        dto.set").append(capitalized).append("(source.").append(getter).append("());\n");
            }
        }

        JavaFileObject file = filer.createSourceFile(qualifiedName, entityType);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %1$s;

                    import org.springframework.stereotype.Component;
                    import ro.cristivoicu.springbootrestless.mapper.Mapper;

                    /**
                     * Generated by RestlessEntityProcessor: no hand-written %2$s existed, so every
                     * %4$s field not annotated {@literal @}RestlessMapperExclude is copied from the
                     * matching %3$s field by name via an explicit setter call. Do not edit -
                     * regenerated on every build; write %2$s by hand instead the moment this
                     * default stops being enough.
                     */
                    @Component
                    public class %2$s implements Mapper<%3$s, %4$s> {
                        @Override
                        public %4$s map(%3$s source) {
                            %4$s dto = new %4$s();
                    %5$s        return dto;
                        }
                    }
                    """.formatted(packageName, simpleName, entityName, dtoType, assignments));
        }
        return qualifiedName;
    }

    /**
     * Every field name on {@code entityType}'s own class hierarchy (up to, not including, {@code
     * Object}) that {@code Default{Create,Update,Patch}DataSource} must never let a DTO's {@code
     * BeanUtils.copyProperties} write into - {@code @Id}/{@code @Version}/the two Spring Data
     * auditing annotations, plus {@code "deleted"} when the entity implements {@link
     * #SOFT_DELETABLE_INTERFACE}. Walks superclasses the same reason {@code resolveIdType}
     * arguably should but doesn't yet - any of these can live on a shared {@code
     * @MappedSuperclass} (e.g. {@code AbstractAuditableEntity}) rather than the concrete entity.
     */
    private Set<String> reservedEntityFieldNames(TypeElement entityType) {
        Set<String> names = new LinkedHashSet<>();
        boolean softDeletable = false;
        for (TypeElement type = entityType; type != null && !type.getQualifiedName().contentEquals("java.lang.Object");
             type = asTypeElement(type.getSuperclass())) {
            for (Element enclosed : type.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) {
                    continue;
                }
                for (AnnotationMirror mirror : enclosed.getAnnotationMirrors()) {
                    String fqName = mirror.getAnnotationType().toString();
                    if (fqName.equals(ID_ANNOTATION) || fqName.equals(VERSION_ANNOTATION)
                            || fqName.equals(CREATED_DATE_ANNOTATION) || fqName.equals(LAST_MODIFIED_DATE_ANNOTATION)) {
                        names.add(enclosed.getSimpleName().toString());
                    }
                }
            }
            for (TypeMirror iface : type.getInterfaces()) {
                if (iface.toString().equals(SOFT_DELETABLE_INTERFACE)) {
                    softDeletable = true;
                }
            }
        }
        if (softDeletable) {
            names.add("deleted");
        }
        return names;
    }

    private static TypeElement asTypeElement(TypeMirror mirror) {
        if (!(mirror instanceof DeclaredType declared)) {
            return null;
        }
        return declared.asElement() instanceof TypeElement typeElement ? typeElement : null;
    }

    /** Emits a compile error for every field on {@code dtoQualifiedName} whose name shadows a {@link #reservedEntityFieldNames} entry - see the call sites' own comment for why this matters. */
    private void checkNoProtectedFields(String dtoQualifiedName, Set<String> reservedNames, String dtoKind, TypeElement entityType) {
        if (reservedNames.isEmpty()) {
            return;
        }
        TypeElement dtoElement = elements.getTypeElement(dtoQualifiedName);
        if (dtoElement == null) {
            return;
        }
        for (Element enclosed : dtoElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            String fieldName = enclosed.getSimpleName().toString();
            if (reservedNames.contains(fieldName)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@RestlessEntity: " + dtoKind + " field '" + fieldName + "' shadows a protected property of "
                                + entityType.getSimpleName() + " (@Id/@Version/soft-delete/audit) - a client-controlled "
                                + "value for it would bypass Default*DataSource's mass-assignment protection; rename "
                                + "or remove this field", enclosed);
            }
        }
    }

    /** Emits a compile error for every primitive field on a {@code PatchModel} - see Ground rules item 6: a primitive can never represent "the client didn't send this", so PATCH would always overwrite it. */
    private void checkPatchModelHasNoPrimitiveFields(TypeElement patchModelElement) {
        for (Element enclosed : patchModelElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (enclosed.asType().getKind().isPrimitive()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@RestlessEntity: PatchModel field '" + enclosed.getSimpleName() + "' is primitive ("
                                + enclosed.asType() + ") - PATCH can never represent \"the client didn't send this "
                                + "field\" for a primitive (it's never null), so every PATCH would overwrite it; "
                                + "use the boxed type instead", enclosed);
            }
        }
    }

    /**
     * Best-effort: resolves the {@code P} type argument of whatever {@code patchDataSource}
     * resolves to, by reading its direct supertype's own type arguments - exactly the shape
     * {@code class FooPatchDataSource extends DefaultPatchDataSource<Foo, Long, FooPatchModel>}
     * (every example in {@code RestlessEntity#patchDataSource}'s own javadoc) has. {@code null}
     * (no check performed, not an error) when {@code patchDataSource} isn't set, or its
     * supertype isn't a plain 3-argument parameterized type - a hand-written {@code
     * PatchDataSource} with a more unusual shape (e.g. implementing an intermediate interface)
     * is still free to shadow a protected field; this is a safety net for the common case, not a
     * guarantee.
     */
    private TypeElement resolvePatchModelType(VerbOverride patchDataSource) {
        if (!patchDataSource.isOverridden()) {
            return null;
        }
        TypeElement overrideElement = elements.getTypeElement(patchDataSource.typeFqn());
        if (overrideElement == null) {
            return null;
        }
        if (!(overrideElement.getSuperclass() instanceof DeclaredType superclass) || superclass.getTypeArguments().size() != 3) {
            return null;
        }
        return asTypeElement(superclass.getTypeArguments().get(2));
    }

    private String resolveIdType(TypeElement entityType) {
        for (Element enclosed : entityType.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            for (AnnotationMirror mirror : enclosed.getAnnotationMirrors()) {
                if (mirror.getAnnotationType().toString().equals(ID_ANNOTATION)) {
                    return enclosed.asType().toString();
                }
            }
        }
        return null;
    }

    /**
     * Reads a {@code Class<?>} attribute of {@code @RestlessEntity} via the {@link
     * AnnotationMirror}/{@link AnnotationValue} API rather than calling the generated annotation
     * accessor directly — the latter throws {@code MirroredTypeException} when the referenced
     * class is (as here) part of the same compilation, since {@code Class} objects can't be
     * loaded for types still being compiled. {@code getElementValuesWithDefaults} (not just
     * {@code getElementValues}) so an un-set attribute still resolves to its {@code Void.class}
     * default instead of coming back empty.
     */
    private TypeMirror readClassAttribute(TypeElement entityType, String attributeName) {
        for (AnnotationMirror mirror : entityType.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(RestlessEntity.class.getCanonicalName())) {
                continue;
            }
            Map<? extends ExecutableElement, ? extends AnnotationValue> values = elements.getElementValuesWithDefaults(mirror);
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals(attributeName)) {
                    return (TypeMirror) entry.getValue().getValue();
                }
            }
        }
        return null;
    }

    private boolean isVoidSentinel(TypeMirror mirror) {
        return mirror == null || mirror.toString().equals(VOID_SENTINEL);
    }

    /**
     * A DTO/mapper attribute: the annotation's own override if set, otherwise the
     * naming-convention sibling ({@code {Entity}{suffix}} in the entity's package) - errors via
     * the messager (returns null) if neither exists.
     */
    private String resolveDtoType(TypeElement entityType, String attributeName, String packageName,
                                   String entityName, String conventionSuffix) {
        TypeMirror override = readClassAttribute(entityType, attributeName);
        if (!isVoidSentinel(override)) {
            return override.toString();
        }
        String qualifiedName = packageName + "." + entityName + conventionSuffix;
        if (elements.getTypeElement(qualifiedName) == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@RestlessEntity: expected " + qualifiedName
                    + " to exist (naming convention: {Entity}" + conventionSuffix
                    + " in the same package), or set " + attributeName + " explicitly", entityType);
            return null;
        }
        return qualifiedName;
    }

    private VerbOverride resolveVerbOverride(TypeElement entityType, String attributeName) {
        TypeMirror override = readClassAttribute(entityType, attributeName);
        return isVoidSentinel(override) ? new VerbOverride(null) : new VerbOverride(override.toString());
    }

    private String resolveOrGenerateRepository(String packageName, String entityName, String idType, Element origin) throws IOException {
        String simpleName = entityName + "Repository";
        String qualifiedName = packageName + "." + simpleName;
        if (elements.getTypeElement(qualifiedName) != null) {
            return simpleName;
        }

        JavaFileObject file = filer.createSourceFile(qualifiedName, origin);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %s;

                    import org.springframework.stereotype.Repository;
                    import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

                    @Repository
                    public interface %s extends SpecificationRepository<%s, %s> {
                    }
                    """.formatted(packageName, simpleName, entityName, idType));
        }
        return simpleName;
    }

    private void writeResourceClass(String packageName, String entityName, String idType,
                                     String createModel, String updateModel, String searchDto, String mapper,
                                     String repository, String basePath, String version,
                                     RestlessOperation[] operations, boolean allowAll,
                                     boolean createEnabled, boolean updateEnabled,
                                     VerbOverride createDataSource, VerbOverride readDataSource,
                                     VerbOverride updateDataSource, VerbOverride deleteDataSource,
                                     VerbOverride authorizationGuard, VerbOverride patchDataSource,
                                     Element origin) throws IOException {
        String resourceName = entityName + "RestlessResource";

        // Forwarded onto the generated @RestlessResource verbatim - RestlessRegistrar (not this
        // processor) is what actually acts on it, see RestlessEntity#version's javadoc for why.
        // String.isBlank() (JDK, not a hand-rolled check) rather than a Spring/Apache utility -
        // this module deliberately carries no dependencies at all (see its pom.xml).
        String restlessResourceAttrs = !version.isBlank()
                ? "basePath = \"%s\", version = \"%s\", allowAll = %s".formatted(basePath, version, allowAll)
                : "basePath = \"%s\", allowAll = %s".formatted(basePath, allowAll);

        StringBuilder extraParams = new StringBuilder();
        // create/update only get a field/constructor-param/init at all when their verb is
        // enabled - see the "createEnabled"/"updateEnabled" javadoc note in generate(). Unlike
        // guard/patch below, RestlessResourceHandler#getCreateDataSource/getUpdateDataSource
        // aren't abstract (they throw a "never overrides" default), so simply not overriding them
        // here when disabled is exactly the hand-written-resource-class shape too.
        String createInit = createEnabled
                ? verbInit(createDataSource, extraParams, "createDataSource",
                        "new DefaultCreateDataSource<>(repository, %s.class, %s.class)".formatted(entityName, createModel))
                : null;
        String readInit = verbInit(readDataSource, extraParams, "readDataSource",
                "new DefaultReadDataSource<>(repository, %s.class)".formatted(searchDto));
        String updateInit = updateEnabled
                ? verbInit(updateDataSource, extraParams, "updateDataSource",
                        "new DefaultUpdateDataSource<>(repository, %s.class)".formatted(updateModel))
                : null;
        // Ground rules Phase 2 item 10: threads the app's real, DI-scoped ConversionService
        // into the default delete data source (a custom Converter bean the consumer registered
        // would otherwise be silently never consulted during bulk delete - see
        // DefaultDeleteDataSource's own javadoc) - this generated resource's own constructor
        // gets it as a fixed parameter (same as repository/mapper) below, Spring resolving it
        // like any other bean dependency.
        String deleteInit = verbInit(deleteDataSource, extraParams, "deleteDataSource",
                "new DefaultDeleteDataSource<>(repository, %s.class, conversionService)".formatted(idType));

        String createFieldDecl = "";
        String createAssignment = "";
        String createGetterMethod = "";
        if (createEnabled) {
            createFieldDecl = "    private final CreateDataSource<" + entityName + ", " + idType + ", ?> createDataSource;";
            createAssignment = "        this.createDataSource = " + createInit + ";";
            createGetterMethod = "\n"
                    + "    @Override\n"
                    + "    protected CreateDataSource<" + entityName + ", " + idType + ", ?> getCreateDataSource() {\n"
                    + "        return createDataSource;\n"
                    + "    }\n";
        }

        String updateFieldDecl = "";
        String updateAssignment = "";
        String updateGetterMethod = "";
        if (updateEnabled) {
            updateFieldDecl = "    private final UpdateDataSource<" + entityName + ", " + idType + ", ?> updateDataSource;";
            updateAssignment = "        this.updateDataSource = " + updateInit + ";";
            updateGetterMethod = "\n"
                    + "    @Override\n"
                    + "    protected UpdateDataSource<" + entityName + ", " + idType + ", ?> getUpdateDataSource() {\n"
                    + "        return updateDataSource;\n"
                    + "    }\n";
        }

        // Unlike the four *DataSource verbs above (always present, defaulting to a Default*
        // instance when not overridden), an unset authorizationGuard means "no override at all" -
        // RestlessResourceHandler's own getAuthorizationGuard() (AuthorizationGuard.allowAll())
        // applies unchanged, exactly like a hand-written resource that never overrides it. So
        // these three stay empty rather than getting a default-expression fallback like verbInit
        // gives the DataSource verbs.
        String guardFieldDecl = "";
        String guardAssignment = "";
        String guardMethod = "";
        if (authorizationGuard.isOverridden()) {
            String guardType = authorizationGuard.typeFqn();
            extraParams.append(", ").append(guardType).append(" authorizationGuard");
            guardFieldDecl = "    private final %s authorizationGuard;\n".formatted(guardType);
            guardAssignment = "        this.authorizationGuard = authorizationGuard;\n";
            // Built via plain concatenation, not a nested text block: a text block's own
            // indentation would get stripped down to its own minimum margin independently of
            // where %18$s ends up sitting in the outer template, landing this method's body at
            // column 0 instead of matching its sibling methods' indentation (getSelectMapper()
            // etc. above it). Explicit literal spaces here are never subject to that stripping.
            guardMethod = "\n"
                    + "    @Override\n"
                    + "    protected AuthorizationGuard<" + entityName + "> getAuthorizationGuard() {\n"
                    + "        return authorizationGuard;\n"
                    + "    }\n";
        }

        // Same "unset means no override, RestlessResourceHandler's own default applies" shape as
        // the guard above - here that default is Optional.empty() (no PATCH route registered at
        // all), not a permissive fallback.
        String patchFieldDecl = "";
        String patchAssignment = "";
        String patchMethod = "";
        if (patchDataSource.isOverridden()) {
            String patchType = patchDataSource.typeFqn();
            extraParams.append(", ").append(patchType).append(" patchDataSource");
            patchFieldDecl = "    private final %s patchDataSource;\n".formatted(patchType);
            patchAssignment = "        this.patchDataSource = patchDataSource;\n";
            patchMethod = "\n"
                    + "    @Override\n"
                    + "    public java.util.Optional<PatchDataSource<" + entityName + ", " + idType + ", ?>> getPatchDataSource() {\n"
                    + "        return java.util.Optional.of(patchDataSource);\n"
                    + "    }\n";
        }

        // Unset (the default, all nine RestlessOperation values) means no override at all -
        // RestlessResourceHandler#getEnabledOperations already defaults to ALL_OPERATIONS, so
        // generating an identical override would be redundant. Only a genuine subset gets one,
        // same "unset means don't touch the base class's own default" shape as guard/patch above.
        String operationsMethod = "";
        Set<RestlessOperation> operationSet = Set.of(operations);
        if (!operationSet.containsAll(Set.of(RestlessOperation.values()))) {
            StringBuilder actions = new StringBuilder();
            for (RestlessOperation op : RestlessOperation.values()) {
                if (!operationSet.contains(op)) {
                    continue;
                }
                if (!actions.isEmpty()) {
                    actions.append(", ");
                }
                actions.append("AuthorizationGuard.Action.").append(op.name());
            }
            operationsMethod = "\n"
                    + "    @Override\n"
                    + "    public java.util.Set<AuthorizationGuard.Action> getEnabledOperations() {\n"
                    + "        return java.util.Set.of(" + actions + ");\n"
                    + "    }\n";
        }

        JavaFileObject file = filer.createSourceFile(packageName + "." + resourceName, origin);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %1$s;

                    import org.springframework.stereotype.Component;
                    import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
                    import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
                    import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
                    import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
                    import ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource;
                    import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
                    import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
                    import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
                    import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
                    import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
                    import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
                    import ro.cristivoicu.springbootrestless.mapper.Mapper;
                    import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

                    /**
                     * Generated by RestlessEntityProcessor from {@literal @}RestlessEntity on {@link %2$s}.
                     * Do not edit - regenerated on every build.
                     */
                    @Component
                    @RestlessResource(%3$s)
                    public class %4$s extends RestlessResourceHandler<%2$s, %5$s> {

                    %19$s
                        private final ReadDataSource<%2$s, %5$s, ?> readDataSource;
                    %20$s
                        private final DeleteDataSource<%2$s, %5$s, ?> deleteDataSource;
                        private final %9$s mapper;
                    %16$s
                        public %4$s(%10$s repository, %9$s mapper, org.springframework.core.convert.ConversionService conversionService%11$s) {
                    %21$s
                            this.readDataSource = %13$s;
                    %22$s
                            this.deleteDataSource = %15$s;
                            this.mapper = mapper;
                    %17$s    }
                    %23$s
                        @Override
                        protected ReadDataSource<%2$s, %5$s, ?> getReadDataSource() {
                            return readDataSource;
                        }
                    %24$s
                        @Override
                        protected DeleteDataSource<%2$s, %5$s, ?> getDeleteDataSource() {
                            return deleteDataSource;
                        }

                        @Override
                        protected Mapper<%2$s, ?> getEntityMapper() {
                            return mapper;
                        }

                        @Override
                        protected Mapper<%2$s, ?> getOverviewMapper() {
                            return mapper;
                        }

                        @Override
                        protected Mapper<%2$s, ?> getSelectMapper() {
                            return mapper;
                        }
                    %18$s}
                    """.formatted(packageName, entityName, restlessResourceAttrs, resourceName, idType,
                    createModel, searchDto, updateModel, mapper, repository, extraParams,
                    createInit, readInit, updateInit, deleteInit,
                    guardFieldDecl + patchFieldDecl, guardAssignment + patchAssignment,
                    guardMethod + patchMethod + operationsMethod,
                    createFieldDecl, updateFieldDecl, createAssignment, updateAssignment,
                    createGetterMethod, updateGetterMethod));
        }
    }

    /**
     * If {@code override} is set, appends a constructor parameter of that type/name to {@code
     * extraParams} (Spring resolves it as a bean, exactly like a hand-written resource bean's
     * constructor would) and returns the parameter name as the field-init expression; otherwise
     * returns {@code defaultExpr} (a {@code new Default*DataSource<>(...)} call) unchanged.
     */
    private String verbInit(VerbOverride override, StringBuilder extraParams, String paramName, String defaultExpr) {
        if (!override.isOverridden()) {
            return defaultExpr;
        }
        extraParams.append(", ").append(override.typeFqn()).append(' ').append(paramName);
        return paramName;
    }
}
