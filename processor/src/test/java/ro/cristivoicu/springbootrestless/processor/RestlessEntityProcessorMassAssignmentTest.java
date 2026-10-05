package ro.cristivoicu.springbootrestless.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real {@code javax.tools.JavaCompiler} in-memory, with {@link RestlessEntityProcessor}
 * on the processor path, to prove Ground rules item 6's two compile-time checks actually fire -
 * {@code Diagnostic.Kind.ERROR} can't be asserted any other way without either breaking the
 * reactor's own build (a genuinely non-compiling fixture can't live in "app") or pulling in a
 * dedicated compile-testing library this module (deliberately dependency-free - see its pom)
 * doesn't otherwise need.
 * <p>
 * Each scenario only needs to resolve far enough to reach the check under test - {@code
 * generate()} keeps going afterward (mapper/repository resolution, code generation) and hits
 * unrelated errors for framework types this dependency-free module has no business stubbing out
 * in full (Spring, {@code app}'s own {@code RestlessResourceHandler}, ...). Assertions key off a
 * specific diagnostic message substring for exactly that reason, rather than "compilation
 * succeeded/failed" as a whole.
 */
class RestlessEntityProcessorMassAssignmentTest {

    private static final String JAKARTA_ID_STUB = """
            package jakarta.persistence;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            public @interface Id {}
            """;

    @Test
    void createModelShadowingTheIdFieldFailsCompilation() {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compile(
                source("jakarta.persistence.Id", JAKARTA_ID_STUB),
                source("test.Thing", """
                        package test;
                        import jakarta.persistence.Id;
                        import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
                        @RestlessEntity(basePath = "/things")
                        public class Thing {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """),
                source("test.ThingCreateModel", """
                        package test;
                        public class ThingCreateModel {
                            private String name;
                            private Long id;
                        }
                        """),
                source("test.ThingUpdateModel", """
                        package test;
                        public class ThingUpdateModel {
                            private String name;
                        }
                        """),
                source("test.ThingSearchDto", """
                        package test;
                        public class ThingSearchDto {
                        }
                        """)
        );

        assertThat(messagesContaining(diagnostics, "shadows a protected property"))
                .anySatisfy(message -> assertThat(message).contains("CreateModel field 'id'"));
    }

    @Test
    void createModelWithNoReservedFieldNamesRaisesNoShadowingError() {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compile(
                source("jakarta.persistence.Id", JAKARTA_ID_STUB),
                source("test.Thing", """
                        package test;
                        import jakarta.persistence.Id;
                        import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
                        @RestlessEntity(basePath = "/things")
                        public class Thing {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """),
                source("test.ThingCreateModel", """
                        package test;
                        public class ThingCreateModel {
                            private String name;
                        }
                        """),
                source("test.ThingUpdateModel", """
                        package test;
                        public class ThingUpdateModel {
                            private String name;
                        }
                        """),
                source("test.ThingSearchDto", """
                        package test;
                        public class ThingSearchDto {
                        }
                        """)
        );

        assertThat(messagesContaining(diagnostics, "shadows a protected property")).isEmpty();
    }

    @Test
    void primitivePatchModelFieldFailsCompilation() {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compile(patchScenarioSources("private boolean flag;"));

        assertThat(messagesContaining(diagnostics, "is primitive"))
                .anySatisfy(message -> assertThat(message).contains("flag").contains("PatchModel"));
    }

    @Test
    void boxedPatchModelFieldRaisesNoPrimitiveError() {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compile(patchScenarioSources("private Boolean flag;"));

        assertThat(messagesContaining(diagnostics, "is primitive")).isEmpty();
    }

    private JavaFileObject[] patchScenarioSources(String patchModelFieldDecl) {
        return new JavaFileObject[]{
                source("jakarta.persistence.Id", JAKARTA_ID_STUB),
                source("test.Thing", """
                        package test;
                        import jakarta.persistence.Id;
                        import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
                        @RestlessEntity(basePath = "/things", patchDataSource = ThingPatchDataSource.class)
                        public class Thing {
                            @Id
                            private Long id;
                            private String name;
                        }
                        """),
                source("test.ThingCreateModel", """
                        package test;
                        public class ThingCreateModel {
                            private String name;
                        }
                        """),
                source("test.ThingUpdateModel", """
                        package test;
                        public class ThingUpdateModel {
                            private String name;
                        }
                        """),
                source("test.ThingSearchDto", """
                        package test;
                        public class ThingSearchDto {
                        }
                        """),
                source("test.ThingDto", """
                        package test;
                        public class ThingDto {
                            private Long id;
                            private String name;
                        }
                        """),
                source("test.StubPatchDataSource", """
                        package test;
                        public class StubPatchDataSource<E, K, P> {
                        }
                        """),
                source("test.ThingPatchModel", """
                        package test;
                        public class ThingPatchModel {
                            %s
                        }
                        """.formatted(patchModelFieldDecl)),
                source("test.ThingPatchDataSource", """
                        package test;
                        public class ThingPatchDataSource extends StubPatchDataSource<Thing, Long, ThingPatchModel> {
                        }
                        """)
        };
    }

    private static List<String> messagesContaining(List<Diagnostic<? extends JavaFileObject>> diagnostics, String substring) {
        return diagnostics.stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .filter(message -> message.contains(substring))
                .collect(Collectors.toList());
    }

    private static List<Diagnostic<? extends JavaFileObject>> compile(JavaFileObject... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try {
            // -s/-d: without an explicit output directory, the generated ThingMapper/Repository/
            // RestlessResource sources this processor writes land relative to the JVM's working
            // directory (surefire forks with cwd = the module root) instead of somewhere disposable.
            java.nio.file.Path generatedOutput = java.nio.file.Files.createTempDirectory("restless-processor-test");
            generatedOutput.toFile().deleteOnExit();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, null, collector,
                    List.of("-classpath", System.getProperty("java.class.path"), "-proc:only",
                            "-s", generatedOutput.toString(), "-d", generatedOutput.toString()),
                    null, List.of(sources));
            task.setProcessors(List.of(new RestlessEntityProcessor()));
            task.call();
            return collector.getDiagnostics();
        } catch (java.io.IOException e) {
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
