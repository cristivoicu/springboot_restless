package ro.cristivoicu.springbootrestless.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Id} declared on a {@code @MappedSuperclass} rather than on the annotated entity itself -
 * the usual shape of a project with a shared {@code BaseEntity} (id + audit columns). Same
 * in-memory {@code JavaCompiler} approach as {@link RestlessEntityProcessorMassAssignmentTest};
 * assertions key off the id-resolution diagnostic and the generated repository's id type argument.
 */
class RestlessEntityProcessorInheritedIdTest {

    private static final String JAKARTA_ID_STUB = """
            package jakarta.persistence;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            public @interface Id {}
            """;

    private static final String NO_ID_ERROR = "has no field annotated @jakarta.persistence.Id";

    @Test
    void idOnASuperclassIsFound() throws IOException {
        Compilation result = compile(
                source("test.BaseEntity", """
                        package test;
                        import jakarta.persistence.Id;
                        public abstract class BaseEntity {
                            @Id
                            private java.util.UUID id;
                        }
                        """),
                thing("BaseEntity"));

        assertThat(result.errorsContaining(NO_ID_ERROR)).isEmpty();
        assertThat(result.generated("test/ThingRepository.java"))
                .contains("SpecificationRepository<Thing, java.util.UUID>");
    }

    @Test
    void idOnAGenericSuperclassResolvesToTheTypeArgument() throws IOException {
        Compilation result = compile(
                source("test.BaseEntity", """
                        package test;
                        import jakarta.persistence.Id;
                        public abstract class BaseEntity<K> {
                            @Id
                            private K id;
                        }
                        """),
                thing("BaseEntity<Long>"));

        assertThat(result.errorsContaining(NO_ID_ERROR)).isEmpty();
        assertThat(result.generated("test/ThingRepository.java"))
                .contains("SpecificationRepository<Thing, java.lang.Long>");
    }

    @Test
    void noIdAnywhereInTheHierarchyStillFailsCompilation() throws IOException {
        Compilation result = compile(
                source("test.BaseEntity", """
                        package test;
                        public abstract class BaseEntity {
                            private java.util.UUID id;
                        }
                        """),
                thing("BaseEntity"));

        assertThat(result.errorsContaining(NO_ID_ERROR)).isNotEmpty();
    }

    /** The annotated entity (no own {@code @Id}) plus the three DTOs id resolution needs to get past. */
    private static JavaFileObject[] thing(String superclass) {
        return new JavaFileObject[]{
                source("test.Thing", """
                        package test;
                        import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
                        @RestlessEntity(basePath = "/things")
                        public class Thing extends %s {
                            private String name;
                        }
                        """.formatted(superclass)),
                source("test.ThingCreateModel", "package test; public class ThingCreateModel { private String name; }"),
                source("test.ThingUpdateModel", "package test; public class ThingUpdateModel { private String name; }"),
                source("test.ThingSearchDto", "package test; public class ThingSearchDto { }"),
                source("test.ThingDto", "package test; public class ThingDto { private String name; }"),
        };
    }

    private record Compilation(List<Diagnostic<? extends JavaFileObject>> diagnostics, Path generatedOutput) {

        List<String> errorsContaining(String substring) {
            return diagnostics.stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> d.getMessage(null))
                    .filter(message -> message.contains(substring))
                    .collect(Collectors.toList());
        }

        String generated(String relativePath) throws IOException {
            return Files.readString(generatedOutput.resolve(relativePath));
        }
    }

    private static Compilation compile(JavaFileObject base, JavaFileObject... rest) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        Path generatedOutput = Files.createTempDirectory("restless-processor-test");
        generatedOutput.toFile().deleteOnExit();
        List<JavaFileObject> sources = new java.util.ArrayList<>(List.of(source("jakarta.persistence.Id", JAKARTA_ID_STUB), base));
        sources.addAll(List.of(rest));
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, null, collector,
                List.of("-classpath", System.getProperty("java.class.path"), "-proc:only",
                        "-s", generatedOutput.toString(), "-d", generatedOutput.toString()),
                null, sources);
        task.setProcessors(List.of(new RestlessEntityProcessor()));
        task.call();
        return new Compilation(collector.getDiagnostics(), generatedOutput);
    }

    private static JavaFileObject source(String qualifiedName, String content) {
        String path = qualifiedName.replace('.', '/') + ".java";
        return new SimpleJavaFileObject(URI.create("string:///" + path), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }

}
