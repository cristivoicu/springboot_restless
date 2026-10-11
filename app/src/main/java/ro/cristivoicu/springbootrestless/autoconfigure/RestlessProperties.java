package ro.cristivoicu.springbootrestless.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

/**
 * App-wide, {@code restless.*}-prefixed tunables for every {@code RestlessResourceHandler} in the
 * context - bound and supplied via {@code RestlessAutoConfiguration}, not read directly by any
 * resource (see {@code RestlessRegistrar}, the one caller). Kept as one small properties class
 * (not scattered {@code @Value}s) so every consumer-facing knob this framework exposes is
 * discoverable from one place, with IDE completion via {@code spring-configuration-metadata.json}
 * (generated automatically by {@code spring-boot-configuration-processor} if the consumer's own
 * build enables annotation processing for it - no extra dependency needed here either way, since
 * {@code @ConfigurationProperties} itself lives in plain {@code spring-boot}, already a
 * transitive dependency via {@code spring-boot-starter}).
 */
@ConfigurationProperties("restless")
public class RestlessProperties {

    @NestedConfigurationProperty
    private final List list = new List();

    @NestedConfigurationProperty
    private final Page page = new Page();

    @NestedConfigurationProperty
    private final Bulk bulk = new Bulk();

    @NestedConfigurationProperty
    private final SoftDelete softDelete = new SoftDelete();

    @NestedConfigurationProperty
    private final Authorization authorization = new Authorization();

    @NestedConfigurationProperty
    private final Immutable immutable = new Immutable();

    public List getList() {
        return list;
    }

    public Page getPage() {
        return page;
    }

    public Bulk getBulk() {
        return bulk;
    }

    public SoftDelete getSoftDelete() {
        return softDelete;
    }

    /**
     * {@code restless.collection-get}: what a plain {@code GET {basePath}} returns on resources that
     * don't choose - {@code page} (default) or {@code list}. See {@code RestlessCollectionGet}.
     */
    private ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet collectionGet =
            ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet.PAGE;

    public ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet getCollectionGet() {
        return collectionGet;
    }

    public void setCollectionGet(ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet collectionGet) {
        this.collectionGet = collectionGet;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public Immutable getImmutable() {
        return immutable;
    }

    /**
     * {@code restless.authorization.deny-as}: what a denied caller gets on every resource that
     * doesn't set its own {@code denyAs} - {@code forbidden} (403, the default) or {@code
     * not-found} (404, indistinguishable from a missing id). See {@code RestlessDenial}.
     */
    public static class Authorization {
        private ro.cristivoicu.springbootrestless.annotation.RestlessDenial denyAs =
                ro.cristivoicu.springbootrestless.annotation.RestlessDenial.FORBIDDEN;

        public ro.cristivoicu.springbootrestless.annotation.RestlessDenial getDenyAs() {
            return denyAs;
        }

        public void setDenyAs(ro.cristivoicu.springbootrestless.annotation.RestlessDenial denyAs) {
            this.denyAs = denyAs;
        }
    }

    /**
     * {@code restless.immutable.on-update}: what {@code @RestlessImmutable(onUpdate = DEFAULT)}
     * does when an update/patch tries to change the field - {@code ignore} (the default) or
     * {@code reject} (400).
     */
    public static class Immutable {
        private ro.cristivoicu.springbootrestless.annotation.RestlessImmutable.OnChange onUpdate =
                ro.cristivoicu.springbootrestless.annotation.RestlessImmutable.OnChange.IGNORE;

        public ro.cristivoicu.springbootrestless.annotation.RestlessImmutable.OnChange getOnUpdate() {
            return onUpdate;
        }

        public void setOnUpdate(ro.cristivoicu.springbootrestless.annotation.RestlessImmutable.OnChange onUpdate) {
            this.onUpdate = onUpdate;
        }
    }

    /** {@code restless.list.*} - see {@link RestlessResourceHandler#DEFAULT_MAX_LIST_SIZE}'s own javadoc for why this cap exists at all. */
    public static class List {

        private int maxSize = RestlessResourceHandler.DEFAULT_MAX_LIST_SIZE;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /**
     * {@code restless.page.*} - caps the client-supplied {@code size} query parameter on {@code
     * GET .../page}/{@code .../page/overview}/{@code .../page/select}/any named {@code
     * getCustomReadActions()} route, every one of which otherwise lets a client ask for an
     * arbitrarily large page in one request. Rejected with {@code 400}, not silently clamped - a
     * client asking for 50,000 rows and quietly getting 2,000 back with no indication looks
     * exactly like "that's all there is", which is worse than an explicit error (see
     * {@link RestlessResourceHandler#DEFAULT_MAX_LIST_SIZE}'s own {@code X-Restless-List-Truncated}
     * for the different tradeoff an *unpaginated* route makes, where there's no page size request
     * to even validate against). Default matches Spring Data's own
     * {@code spring.data.web.pageable.max-page-size} default.
     */
    public static class Page {

        private int maxSize = RestlessResourceHandler.DEFAULT_MAX_PAGE_SIZE;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /**
     * {@code restless.bulk.*} - caps how many items a single {@code createBulk}/{@code
     * updateBulk}/{@code deleteAll} request may carry. Rejected with {@code 400} before any of
     * them are processed, same "fail fast, not partial" reasoning {@code
     * RestlessResourceHandler#inTransaction} already applies to the write itself.
     */
    public static class Bulk {

        private int maxSize = RestlessResourceHandler.DEFAULT_MAX_BULK_SIZE;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /**
     * {@code restless.soft-delete.*} (Ground rules Phase 2 item 13) - {@code includeInSingleRead}
     * controls whether {@code findOne}/a named view returns an already-soft-deleted row ({@code
     * true}, the default - today's pre-item-13 behavior) or 404s it like a row that never existed
     * ({@code false}). A write on a soft-deleted row always 404s regardless of this setting - see
     * {@code SoftDeletable}'s own javadoc.
     */
    public static class SoftDelete {

        private boolean includeInSingleRead = true;

        public boolean isIncludeInSingleRead() {
            return includeInSingleRead;
        }

        public void setIncludeInSingleRead(boolean includeInSingleRead) {
            this.includeInSingleRead = includeInSingleRead;
        }
    }
}
