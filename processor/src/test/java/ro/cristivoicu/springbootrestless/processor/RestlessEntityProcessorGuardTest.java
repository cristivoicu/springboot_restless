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
 * Guard wiring in generated resources: a read-only resource returns a {@code
 * ReadAuthorizationGuard}, {@code readGuard}/{@code writeGuard} become one {@code
 * AuthorizationGuards.split(...)}, and a guard type that can't serve its resource is a compile
 * error. Stubs stand in for the guard interfaces and the write handler, the same {@code
 * -proc:only} approach as {@link RestlessEntityProcessorReadOnlyTest}.
 */
class RestlessEntityProcessorGuardTest {

    private static final List<JavaFileObject> RUNTIME_STUBS = List.of(
            source("jakarta.persistence.Id", """
                    package jakarta.persistence;
                    import java.lang.annotation.*;
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.FIELD)
                    public @interface Id {}
                    """),
            source("ro.cristivoicu.springbootrestless.authorization.ReadAuthorizationGuard", """
                    package ro.cristivoicu.springbootrestless.authorization;
                    public interface ReadAuthorizationGuard<E> {}
                    """),
            source("ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard", """
                    package ro.cristivoicu.springbootrestless.authorization;
                    public interface AuthorizationGuard<E> extends ReadAuthorizationGuard<E> {}
                    """),
            source("ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler", """
                    package ro.cristivoicu.springbootrestless.resource;
                    public abstract class RestlessResourceHandler<E, K> {}
                    """),
            source("test.ThingReadGuard", """
                    package test;
                    public class ThingReadGuard implements ro.cristivoicu.springbootrestless.authorization.ReadAuthorizationGuard<Thing> {}
                    """),
            source("test.ThingWriteGuard", """
                    package test;
                    public class ThingWriteGuard implements ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard<Thing> {}
                    """));

    @Test
    void aReadOnlyResourceAcceptsAReadGuardAndReturnsTheReadType() throws IOException {
        Result result = compile("@RestlessReadEntity(basePath = \"/things\", authorizationGuard = ThingReadGuard.class)");

        assertThat(result.errors()).isEmpty();
        assertThat(result.resource())
                .contains("protected ReadAuthorizationGuard<Thing> getAuthorizationGuard()")
                .contains("test.ThingReadGuard authorizationGuard");
    }

    @Test
    void aReadWriteResourceRejectsAReadOnlyGuard() {
        Result result = compile("@RestlessEntity(basePath = \"/things\", authorizationGuard = ThingReadGuard.class)");

        assertThat(result.errors()).anyMatch(message -> message.contains("authorizationGuard ThingReadGuard must implement AuthorizationGuard"));
    }

    @Test
    void readAndWriteGuardsAreCombinedWithSplit() throws IOException {
        Result result = compile("@RestlessEntity(basePath = \"/things\", readGuard = ThingReadGuard.class, writeGuard = ThingWriteGuard.class)");

        assertThat(result.errors()).isEmpty();
        assertThat(result.resource())
                .contains("test.ThingReadGuard readGuard, test.ThingWriteGuard writeGuard")
                .contains("AuthorizationGuards.split(readGuard, writeGuard)")
                .contains("protected AuthorizationGuard<Thing> getAuthorizationGuard()")
                .contains("extends RestlessResourceHandler<Thing,");
    }

    @Test
    void readGuardAloneIsRejected() {
        Result result = compile("@RestlessEntity(basePath = \"/things\", readGuard = ThingReadGuard.class)");

        assertThat(result.errors()).anyMatch(message -> message.contains("readGuard and writeGuard must be set together"));
    }

    @Test
    void splitGuardsCantBeCombinedWithAuthorizationGuard() {
        Result result = compile("@RestlessEntity(basePath = \"/things\", authorizationGuard = ThingWriteGuard.class, "
                + "readGuard = ThingReadGuard.class, writeGuard = ThingWriteGuard.class)");

        assertThat(result.errors()).anyMatch(message -> message.contains("replace authorizationGuard"));
    }

    @Test
    void aWriteGuardMustBeAFullGuard() {
        Result result = compile("@RestlessEntity(basePath = \"/things\", readGuard = ThingReadGuard.class, writeGuard = ThingReadGuard.class)");

        assertThat(result.errors()).anyMatch(message -> message.contains("writeGuard ThingReadGuard must implement AuthorizationGuard"));
    }

    // ---- fixtures ----

    /** {@code errors}: the processor's own error messages. */
    private record Result(Path output, List<String> errors) {
        String resource() throws IOException {
            Path file = output.resolve("test").resolve("ThingRestlessResource.java");
            assertThat(file).exists();
            return Files.readString(file);
        }
    }

    private static Result compile(String annotation) {
        List<JavaFileObject> sources = new ArrayList<>(RUNTIME_STUBS);
        sources.add(source("test.Thing", """
                package test;
                import jakarta.persistence.Id;
                import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
                import ro.cristivoicu.springbootrestless.annotation.RestlessReadEntity;
                %s
                public class Thing {
                    @Id
                    private Long id;
                    private String name;
                }
                """.formatted(annotation)));
        sources.add(source("test.ThingCreateModel", "package test;\npublic class ThingCreateModel { private String name; }\n"));
        sources.add(source("test.ThingUpdateModel", "package test;\npublic class ThingUpdateModel { private String name; }\n"));
        sources.add(source("test.ThingSearchDto", "package test;\npublic class ThingSearchDto { }\n"));
        sources.add(source("test.ThingDto", "package test;\npublic class ThingDto { private Long id; private String name; }\n"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try {
            Path generatedOutput = Files.createTempDirectory("restless-processor-test");
            generatedOutput.toFile().deleteOnExit();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, null, collector,
                    List.of("-classpath", System.getProperty("java.class.path"), "-proc:only",
                            "-s", generatedOutput.toString(), "-d", generatedOutput.toString()),
                    null, sources);
            task.setProcessors(List.of(new RestlessEntityProcessor()));
            task.call();
            List<String> errors = collector.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getMessage(null))
                    // Only the processor's own errors - generated sources can't resolve Spring here.
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
