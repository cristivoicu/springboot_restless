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

    public List getList() {
        return list;
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
}
