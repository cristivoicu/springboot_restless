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
import java.util.Map;
import java.util.Set;

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
        String mapper = resolveDtoType(entityType, "mapper", packageName, entityName, "Mapper");
        if (createModel == null || updateModel == null || searchDto == null || mapper == null) {
            return; // errors already reported via the messager
        }

        String repository = resolveOrGenerateRepository(packageName, entityName, idType, entityType);

        VerbOverride createDataSource = resolveVerbOverride(entityType, "createDataSource");
        VerbOverride readDataSource = resolveVerbOverride(entityType, "readDataSource");
        VerbOverride updateDataSource = resolveVerbOverride(entityType, "updateDataSource");
        VerbOverride deleteDataSource = resolveVerbOverride(entityType, "deleteDataSource");

        String basePath = entityType.getAnnotation(RestlessEntity.class).basePath();
        writeResourceClass(packageName, entityName, idType, createModel, updateModel, searchDto, mapper,
                repository, basePath, createDataSource, readDataSource, updateDataSource, deleteDataSource, entityType);
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
                                     String repository, String basePath,
                                     VerbOverride createDataSource, VerbOverride readDataSource,
                                     VerbOverride updateDataSource, VerbOverride deleteDataSource,
                                     Element origin) throws IOException {
        String resourceName = entityName + "RestlessResource";

        StringBuilder extraParams = new StringBuilder();
        String createInit = verbInit(createDataSource, extraParams, "createDataSource",
                "new DefaultCreateDataSource<>(repository, %s.class, %s.class)".formatted(entityName, createModel));
        String readInit = verbInit(readDataSource, extraParams, "readDataSource",
                "new DefaultReadDataSource<>(repository, %s.class)".formatted(searchDto));
        String updateInit = verbInit(updateDataSource, extraParams, "updateDataSource",
                "new DefaultUpdateDataSource<>(repository, %s.class)".formatted(updateModel));
        String deleteInit = verbInit(deleteDataSource, extraParams, "deleteDataSource",
                "new DefaultDeleteDataSource<>(repository, %s.class)".formatted(idType));

        JavaFileObject file = filer.createSourceFile(packageName + "." + resourceName, origin);
        try (Writer writer = file.openWriter()) {
            writer.write("""
                    package %1$s;

                    import org.springframework.stereotype.Component;
                    import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
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
                    @RestlessResource(basePath = "%3$s")
                    public class %4$s extends RestlessResourceHandler<%2$s, %5$s> {

                        private final CreateDataSource<%2$s, %5$s, ?> createDataSource;
                        private final ReadDataSource<%2$s, %5$s, ?> readDataSource;
                        private final UpdateDataSource<%2$s, %5$s, ?> updateDataSource;
                        private final DeleteDataSource<%2$s, %5$s, ?> deleteDataSource;
                        private final %9$s mapper;

                        public %4$s(%10$s repository, %9$s mapper%11$s) {
                            this.createDataSource = %12$s;
                            this.readDataSource = %13$s;
                            this.updateDataSource = %14$s;
                            this.deleteDataSource = %15$s;
                            this.mapper = mapper;
                        }

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
                    }
                    """.formatted(packageName, entityName, basePath, resourceName, idType,
                    createModel, searchDto, updateModel, mapper, repository, extraParams,
                    createInit, readInit, updateInit, deleteInit));
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
