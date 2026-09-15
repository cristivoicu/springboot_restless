package ro.cristivoicu.springbootrestless.processor;

import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a {@code {Entity}RestlessResource} class (the same shape a human would hand-write —
 * see {@code DepartmentRestlessResource} in the app module) for every class annotated {@link
 * RestlessEntity}. Sibling DTOs/{@code Mapper} are found by naming convention ({@code
 * {Entity}CreateModel} etc. in the entity's own package) unless overridden via the annotation's
 * attributes; a missing {@code {Entity}Repository} is generated too. Registered via
 * {@code META-INF/services/javax.annotation.processing.Processor}.
 */
@SupportedAnnotationTypes("ro.cristivoicu.springbootrestless.annotation.RestlessEntity")
public class RestlessEntityProcessor extends AbstractProcessor {

    private static final String ID_ANNOTATION = "jakarta.persistence.Id";
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

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
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

        String createModel = resolveDtoType(entityType, "createModel", packageName, entityName, "CreateModel");
        String updateModel = resolveDtoType(entityType, "updateModel", packageName, entityName, "UpdateModel");
        String searchDto = resolveDtoType(entityType, "searchDto", packageName, entityName, "SearchDto");
        if (createModel == null || updateModel == null || searchDto == null) {
            return; // errors already reported via the messager
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

        RestlessEntity restlessEntity = entityType.getAnnotation(RestlessEntity.class);
        writeResourceClass(packageName, entityName, idType, createModel, updateModel, searchDto, mapper,
                repository, restlessEntity.basePath(), restlessEntity.version(), createDataSource, readDataSource,
                updateDataSource, deleteDataSource, authorizationGuard, entityType);
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
     * Generates a {@code {Entity}Mapper implements Mapper<Entity, Dto>} whose {@code map(...)}
     * is one {@code BeanUtils.copyProperties} call - reflective, matching source/target fields by
     * name, exactly the technique {@code DefaultCreateDataSource}/{@code DefaultUpdateDataSource}
     * already use for their own verbs. {@code dtoType} is always fully qualified (whatever {@link
     * #resolveDtoType} returned), so it's safe to use directly in the generated source with no
     * import needed, same as every other DTO type name this processor already writes out.
     */
    private String generateDefaultMapper(String packageName, String entityName, String dtoType, Element origin) throws IOException {
        String simpleName = entityName + "Mapper";
        String qualifiedName = packageName + "." + simpleName;

        List<String> excluded = resolveExcludedMapperFields(dtoType);
        String ignoreArgs = excluded.stream().map(field -> ", \"" + field + "\"").collect(Collectors.joining());

        JavaFileObject file = filer.createSourceFile(qualifiedName, origin);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %1$s;

                    import org.springframework.beans.BeanUtils;
                    import org.springframework.stereotype.Component;
                    import ro.cristivoicu.springbootrestless.mapper.Mapper;

                    /**
                     * Generated by RestlessEntityProcessor: no hand-written %2$s existed, so every
                     * %4$s field not annotated {@literal @}RestlessMapperExclude is copied from the
                     * matching %3$s field by name via BeanUtils.copyProperties. Do not edit -
                     * regenerated on every build; write %2$s by hand instead the moment this
                     * default stops being enough.
                     */
                    @Component
                    public class %2$s implements Mapper<%3$s, %4$s> {
                        @Override
                        public %4$s map(%3$s source) {
                            %4$s dto = new %4$s();
                            BeanUtils.copyProperties(source, dto%5$s);
                            return dto;
                        }
                    }
                    """.formatted(packageName, simpleName, entityName, dtoType, ignoreArgs));
        }
        return qualifiedName;
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
                                     VerbOverride createDataSource, VerbOverride readDataSource,
                                     VerbOverride updateDataSource, VerbOverride deleteDataSource,
                                     VerbOverride authorizationGuard,
                                     Element origin) throws IOException {
        String resourceName = entityName + "RestlessResource";

        // Forwarded onto the generated @RestlessResource verbatim - RestlessRegistrar (not this
        // processor) is what actually acts on it, see RestlessEntity#version's javadoc for why.
        // String.isBlank() (JDK, not a hand-rolled check) rather than a Spring/Apache utility -
        // this module deliberately carries no dependencies at all (see its pom.xml).
        String restlessResourceAttrs = !version.isBlank()
                ? "basePath = \"%s\", version = \"%s\"".formatted(basePath, version)
                : "basePath = \"%s\"".formatted(basePath);

        StringBuilder extraParams = new StringBuilder();
        String createInit = verbInit(createDataSource, extraParams, "createDataSource",
                "new DefaultCreateDataSource<>(repository, %s.class, %s.class)".formatted(entityName, createModel));
        String readInit = verbInit(readDataSource, extraParams, "readDataSource",
                "new DefaultReadDataSource<>(repository, %s.class)".formatted(searchDto));
        String updateInit = verbInit(updateDataSource, extraParams, "updateDataSource",
                "new DefaultUpdateDataSource<>(repository, %s.class)".formatted(updateModel));
        String deleteInit = verbInit(deleteDataSource, extraParams, "deleteDataSource",
                "new DefaultDeleteDataSource<>(repository, %s.class)".formatted(idType));

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

        JavaFileObject file = filer.createSourceFile(packageName + "." + resourceName, origin);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %1$s;

                    import org.springframework.stereotype.Component;
                    import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
                    import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
                    import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
                    import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
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

                        private final CreateDataSource<%2$s, %5$s, ?> createDataSource;
                        private final ReadDataSource<%2$s, %5$s, ?> readDataSource;
                        private final UpdateDataSource<%2$s, %5$s, ?> updateDataSource;
                        private final DeleteDataSource<%2$s, %5$s, ?> deleteDataSource;
                        private final %9$s mapper;
                    %16$s
                        public %4$s(%10$s repository, %9$s mapper%11$s) {
                            this.createDataSource = %12$s;
                            this.readDataSource = %13$s;
                            this.updateDataSource = %14$s;
                            this.deleteDataSource = %15$s;
                            this.mapper = mapper;
                    %17$s    }

                        @Override
                        protected CreateDataSource<%2$s, %5$s, ?> getCreateDataSource() {
                            return createDataSource;
                        }

                        @Override
                        protected ReadDataSource<%2$s, %5$s, ?> getReadDataSource() {
                            return readDataSource;
                        }

                        @Override
                        protected UpdateDataSource<%2$s, %5$s, ?> getUpdateDataSource() {
                            return updateDataSource;
                        }

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
                    createInit, readInit, updateInit, deleteInit, guardFieldDecl, guardAssignment, guardMethod));
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
