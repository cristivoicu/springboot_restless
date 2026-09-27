package ro.cristivoicu.springbootrestless.models;

/**
 * Marker for a named write action's request DTO — the {@code Req} type parameter of
 * {@link ro.cristivoicu.springbootrestless.resource.WriteAction}. Zero-method, same shape as
 * {@link CreateModel}/{@link UpdateModel} — a naming-convention signal, not a technical
 * requirement: {@code RestlessResourceHandler#writeAction} deserializes the request body via
 * Jackson against whatever {@code Class} token {@link
 * ro.cristivoicu.springbootrestless.resource.WriteAction#getRequestType()} returns, the same
 * "readBody(request, Class&lt;?&gt;)" mechanism {@code create}/{@code update}/{@code patch} already
 * use — no method of this marker is ever actually called.
 */
public interface WriteActionRequest {
}
