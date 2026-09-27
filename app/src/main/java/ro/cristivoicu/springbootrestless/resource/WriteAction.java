package ro.cristivoicu.springbootrestless.resource;

import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/**
 * Named custom write action: {@link ReadAction}'s counterpart for a mutation that carries
 * intent beyond CRUD — "promote", "cancel", "recalculate totals" — rather than a full-replace
 * PUT with no vocabulary for illegal-transition prevention. Backs
 * {@code POST {basePath}/{id}/actions/{name}} (see {@code RestlessResourceHandler#writeAction}).
 * <p>
 * Unlike {@link ReadAction} (bypasses {@code Mapper} only on the way <em>in</em> — its own
 * {@code SearchDto} shape, not the response), a write action bypasses {@code Mapper} entirely,
 * both in and out: {@link #execute} returns {@code Resp} directly, whatever shape the action
 * author decides — the same "response shape is always hand-written, never framework-invented"
 * boundary {@link ro.cristivoicu.springbootrestless.mapper.Mapper}'s own javadoc already states.
 * Nothing stops an implementation from calling a real {@code Mapper} internally to build that
 * {@code Resp} — that's just an implementation detail of {@link #execute}, not something this
 * interface prescribes.
 * <p>
 * Persistence is likewise entirely {@link #execute}'s own responsibility — this interface
 * doesn't prescribe how the mutation is saved (a repository call, a {@code *DataSource}, several
 * of either for a multi-step transition), same as {@link ReadAction} doesn't prescribe how its
 * {@code Specification} is built.
 *
 * @param <E>    the entity type this action operates on
 * @param <Req>  this action's own request DTO type
 * @param <Resp> this action's own response type — not necessarily an {@code EntityDto}
 */
public interface WriteAction<E, Req extends WriteActionRequest, Resp> {

    Class<Req> getRequestType();

    Class<Resp> getResponseType();

    /**
     * Runs this action against an already-loaded, already-guard-checked {@code entity} and a
     * validated {@code request} body. Runs inside {@code RestlessResourceHandler}'s own
     * transaction boundary (see {@code writeAction}'s javadoc) — {@code throws Exception} for the
     * same reason {@code UpdateDataSource#update}/{@code PatchDataSource#patch} do: so an
     * implementation can call another {@code throws Exception}-declaring collaborator directly,
     * with no need to wrap a checked exception by hand first.
     */
    Resp execute(E entity, Req request) throws Exception;
}
