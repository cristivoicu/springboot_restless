package ro.cristivoicu.springbootrestless.processor;

import org.junit.jupiter.api.Test;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ground rules Phase 3 item 15 ("Processor codegen"): the generated {@code {Entity}Mapper} used
 * to be one reflective {@code BeanUtils.copyProperties} call; it's now an explicit {@code
 * dto.setX(source.getX())} per matched field pair. Same in-memory {@code javax.tools.JavaCompiler}
 * harness {@code RestlessEntityProcessorMassAssignmentTest} already uses, but reading back the
 * generated {@code ThingMapper.java} source text from disk (via {@code -proc:only}, which stops
 * after annotation processing but still writes every {@link javax.annotation.processing.Filer}-
 * created file) instead of inspecting a diagnostic - there's no diagnostic for "what did the
 * generated mapper look like".
 */
class RestlessEntityProcessorMapperCodegenTest {

    private static final String JAKARTA_ID_STUB = """
            package jakarta.persistence;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            public @interface Id {}
            """;

    @Test
    void generatedMapperUsesExplicitSettersNotBeanUtils() throws IOException {
        String generated = generateThingMapper();

        assertThat(generated).doesNotContain("BeanUtils").doesNotContain("copyProperties");
        assertThat(generated).contains("dto.setName(source.getName());");
    }

    @Test
    void generatedMapperHandlesBooleanGettersAndSkipsUnmatchedOrExcludedFields() throws IOException {
        String generated = generateThingMapper();

        // "active" is a primitive boolean on the entity - Lombok-style isActive(), not getActive().
        assertThat(generated).contains("dto.setActive(source.isActive());");
        // "extra" exists only on the DTO - no matching entity field, silently not copied.
        assertThat(generated).doesNotContain("setExtra");
        // "secret" exists on both, but is @RestlessMapperExclude on the DTO - never copied either.
        assertThat(generated).doesNotContain("setSecret");
    }

    private String generateThingMapper() throws IOException {
        Path generatedOutput = compile(
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
                            private boolean active;
                            private String secret;
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
                        import ro.cristivoicu.springbootrestless.annotation.RestlessMapperExclude;
                        public class ThingDto {
                            private Long id;
                            private String name;
                            private boolean active;
                            private String extra;
                            @RestlessMapperExclude
                            private String secret;
                        }
                        """)
        );

        Path mapperFile = generatedOutput.resolve("test").resolve("ThingMapper.java");
        assertThat(mapperFile).exists();
        return Files.readString(mapperFile);
    }

    /**
     * Same {@code -proc:only} idiom {@code RestlessEntityProcessorMassAssignmentTest} already
     * uses - and the same caveat its own javadoc already documents: {@code generate()} keeps
     * going past mapper generation into generating {@code ThingRestlessResource} too, which this
     * dependency-free module has no business stubbing out in full (Spring, {@code app}'s own
     * {@code RestlessResourceHandler}, ...), so later, unrelated compile errors are expected and
     * ignored here - the {@code ThingMapper.java} this test cares about is already written to
     * disk by the time those later errors happen.
     */
    private static Path compile(JavaFileObject... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try {
            Path generatedOutput = Files.createTempDirectory("restless-processor-test");
            generatedOutput.toFile().deleteOnExit();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, null, collector,
                    List.of("-classpath", System.getProperty("java.class.path"), "-proc:only",
                            "-s", generatedOutput.toString(), "-d", generatedOutput.toString()),
                    null, List.of(sources));
            task.setProcessors(List.of(new RestlessEntityProcessor()));
            task.call();
            return generatedOutput;
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
