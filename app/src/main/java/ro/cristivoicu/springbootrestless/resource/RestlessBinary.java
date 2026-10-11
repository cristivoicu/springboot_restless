package ro.cristivoicu.springbootrestless.resource;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A non-JSON response - a PDF, a ZIP, an image - returned from a named view's {@code Mapper}, a
 * {@link WriteAction}, or a {@link CollectionAction}. The handler writes it straight to the
 * servlet response, still inside the route's transaction, so a body produced from lazily loaded
 * data (or streamed from storage row by row) never outlives its session.
 * <pre>{@code
 * return RestlessBinary.of("application/pdf", out -> renderer.render(event, out))
 *         .filename("seating-chart.pdf").inline();
 * }</pre>
 * Authorization ran before the body is produced, exactly like for a JSON response.
 */
public final class RestlessBinary {

    /** Writes the body. May write any amount; the stream is not buffered by the framework. */
    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }

    private final String contentType;
    private final Writer writer;
    private final Long length;
    private String filename;
    private boolean attachment = true;

    private RestlessBinary(String contentType, Writer writer, Long length) {
        this.contentType = contentType;
        this.writer = writer;
        this.length = length;
    }

    /** A body produced by {@code writer} - the right choice for anything large or generated on the fly. */
    public static RestlessBinary of(String contentType, Writer writer) {
        return new RestlessBinary(contentType, writer, null);
    }

    public static RestlessBinary of(String contentType, byte[] bytes) {
        return new RestlessBinary(contentType, out -> out.write(bytes), (long) bytes.length);
    }

    public static RestlessBinary of(String contentType, Resource resource) throws IOException {
        long length = resource.isFile() ? resource.contentLength() : -1;
        return new RestlessBinary(contentType, out -> {
            try (var in = resource.getInputStream()) {
                in.transferTo(out);
            }
        }, length >= 0 ? length : null);
    }

    /** Suggested file name for {@code Content-Disposition}. */
    public RestlessBinary filename(String filename) {
        this.filename = filename;
        return this;
    }

    /** Shown in the browser ({@code Content-Disposition: inline}) instead of downloaded. */
    public RestlessBinary inline() {
        this.attachment = false;
        return this;
    }

    public String contentType() {
        return contentType;
    }

    Writer writer() {
        return writer;
    }

    Long length() {
        return length;
    }

    String contentDisposition() {
        ContentDisposition.Builder builder = attachment ? ContentDisposition.attachment() : ContentDisposition.inline();
        if (filename != null) {
            builder.filename(filename, StandardCharsets.UTF_8);
        }
        return builder.build().toString();
    }
}
