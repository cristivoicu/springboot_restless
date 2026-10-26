package ro.cristivoicu.springbootrestless.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which base class the generated resource extends: {@code RestlessReadResourceHandler} (read
 * artifact only) for {@code @RestlessReadEntity} and for a {@code @RestlessEntity} with nothing
 * write-related, {@code RestlessResourceHandler} (write artifact) otherwise - and a clear compile
 * error, not unresolved symbols in generated code, when that write artifact is missing.
 * <p>
 * Same {@code -proc:only} idiom as the other processor tests: this module has no runtime on its
 * test classpath, so whether "the write artifact is present" is simulated by passing (or not) a
 * stub {@code RestlessResourceHandler} source, which is what {@code Elements#getTypeElement} sees.
 */
class RestlessEntityProcessorReadOnlyTest {

    private static final String JAKARTA_ID_STUB = """
            package jakarta.persistence;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            public @interface Id {}
            """;

    private static final String WRITE_HANDLER_STUB = """
            package ro.cristivoicu.springbootrestless.resource;
            public abstract class RestlessResourceHandler<E, K> {}
            """;

    @Test
    void readEntityExtendsTheReadHandlerAndNeedsNoWriteModels() throws IOException {
        Result result = compile(false, thing("@RestlessReadEntity(basePath = \"/things\", allowAll = true)",
                "ro.cristivoicu.springbootrestless.annotation.RestlessReadEntity"));

        String resource = result.read("ThingRestlessResource.java");
        assertThat(resource)
                .contains("extends RestlessReadResourceHandler<Thing, java.lang.Long>")
                .doesNotContain(" RestlessResourceHandler<")
                .doesNotContain("CreateDataSource")
                .doesNotContain("DeleteDataSource")
                .doesNotContain("getEnabledOperations");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void readEntityWithASubsetOfReadsOverridesEnabledOperations() throws IOException {
        Result result = compile(false, thing("@RestlessReadEntity(basePath = \"/things\", allowAll = true, "
                        + "operations = {RestlessOperation.READ_ONE, RestlessOperation.READ_PAGE})",
                "ro.cristivoicu.springbootrestless.annotation.RestlessReadEntity",
                "ro.cristivoicu.springbootrestless.annotation.RestlessOperation"));

        assertThat(result.read("ThingRestlessResource.java"))
                .contains("java.util.Set.of(AuthorizationGuard.Action.READ_ONE, AuthorizationGuard.Action.READ_PAGE)");
    }

    @Test
    void readEntityRejectsAWriteOperation() {
        Result result = compile(false, thing("@RestlessReadEntity(basePath = \"/things\", "
                        + "operations = {RestlessOperation.READ_ONE, RestlessOperation.CREATE})",
                "ro.cristivoicu.springbootrestless.annotation.RestlessReadEntity",
                "ro.cristivoicu.springbootrestless.annotation.RestlessOperation"));

        assertThat(result.errors()).anyMatch(message -> message.contains("may only name read operations"));
    }

    @Test
    void restlessEntityWithOnlyReadOperationsIsGeneratedReadOnly() throws IOException {
        Result result = compile(false, thing("@RestlessEntity(basePath = \"/things\", allowAll = true, "
                        + "operations = {RestlessOperation.READ_ONE, RestlessOperation.READ_LIST})",
                "ro.cristivoicu.springbootrestless.annotation.RestlessEntity",
                "ro.cristivoicu.springbootrestless.annotation.RestlessOperation"));

        assertThat(result.read("ThingRestlessResource.java")).contains("extends RestlessReadResourceHandler<Thing, java.lang.Long>");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void restlessEntityWithWritesNeedsTheWriteArtifact() {
        Result result = compile(false, thing("@RestlessEntity(basePath = \"/things\", allowAll = true)",
                "ro.cristivoicu.springbootrestless.annotation.RestlessEntity"));

        assertThat(result.errors()).anyMatch(message -> message.contains("need spring-boot-restless-write on the classpath"));
        assertThat(result.output().resolve("test/ThingRestlessResource.java")).doesNotExist();
    }

    @Test
    void restlessEntityWithWritesExtendsTheWriteHandlerWhenItIsThere() throws IOException {
        Result result = compile(true, thing("@RestlessEntity(basePath = \"/things\", allowAll = true)",
                "ro.cristivoicu.springbootrestless.annotation.RestlessEntity"));

        assertThat(result.read("ThingRestlessResource.java")).contains("extends RestlessResourceHandler<Thing, java.lang.Long>");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void bothAnnotationsTogetherAreRejected() {
        Result result = compile(true, thing("@RestlessEntity(basePath = \"/things\")\n@RestlessReadEntity(basePath = \"/things\")",
                "ro.cristivoicu.springbootrestless.annotation.RestlessEntity",
                "ro.cristivoicu.springbootrestless.annotation.RestlessReadEntity"));

        assertThat(result.errors()).anyMatch(message -> message.contains("can't be combined"));
    }

    // ---- fixtures ----

    /** A {@code Thing} entity carrying {@code annotation}, plus every sibling DTO either annotation might look for. */
    private static List<JavaFileObject> thing(String annotation, String... imports) {
        StringBuilder importLines = new StringBuilder();
        for (String type : imports) {
            importLines.append("import ").append(type).append(";\n");
        }
        return List.of(
                source("jakarta.persistence.Id", JAKARTA_ID_STUB),
                source("test.Thing", """
                        package test;
                        import jakarta.persistence.Id;
                        %s
                        %s
                        public class Thing {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """.formatted(importLines, annotation)),
                source("test.ThingCreateModel", "package test;\npublic class ThingCreateModel { private String name; }\n"),
                source("test.ThingUpdateModel", "package test;\npublic class ThingUpdateModel { private String name; }\n"),
                source("test.ThingSearchDto", "package test;\npublic class ThingSearchDto { }\n"),
                source("test.ThingDto", "package test;\npublic class ThingDto { private Long id; private String name; }\n"));
    }

    /** {@code errors}: the processor's own error messages. */
    private record Result(Path output, List<String> errors) {
        String read(String generatedFile) throws IOException {
            Path file = output.resolve("test").resolve(generatedFile);
            assertThat(file).exists();
            return Files.readString(file);
        }
    }

    private static Result compile(boolean withWriteArtifact, List<JavaFileObject> sources) {
        List<JavaFileObject> all = new ArrayList<>(sources);
        if (withWriteArtifact) {
            all.add(source("ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler", WRITE_HANDLER_STUB));
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try {
            Path generatedOutput = Files.createTempDirectory("restless-processor-test");
            generatedOutput.toFile().deleteOnExit();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, null, collector,
                    List.of("-classpath", System.getProperty("java.class.path"), "-proc:only",
                            "-s", generatedOutput.toString(), "-d", generatedOutput.toString()),
                    null, all);
            task.setProcessors(List.of(new RestlessEntityProcessor()));
            task.call();
            List<String> errors = collector.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getMessage(null))
                    // Only the processor's own errors: generated sources referencing Spring and the
                    // runtime artifacts can't resolve in this dependency-free module, as expected.
                    .filter(message -> message.startsWith("@Restless") || message.startsWith("Failed to generate"))
                    .toList();
            return new Result(generatedOutput, errors);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JavaFileObject source(String qualifiedName, String content) {
        String path = "/" + qualifiedName.replace('.', '/') + ".java";
        return new SimpleJavaFileObject(URI.create("string:///" + path.substring(1)), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }
}
