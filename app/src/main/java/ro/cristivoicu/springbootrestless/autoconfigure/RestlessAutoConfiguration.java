package ro.cristivoicu.springbootrestless.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.error.RestlessExceptionHandler;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import ro.cristivoicu.springbootrestless.openapi.RestlessOpenApiCustomizer;
import ro.cristivoicu.springbootrestless.registry.RestlessRegistrar;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers every infrastructure bean this framework needs (the same set that used to be
 * {@code @Component}-scanned) as plain {@code @Bean} methods instead, discovered automatically by
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} the
 * moment {@code spring-boot-restless} is on the classpath - <b>no consumer {@code @ComponentScan}
 * needed any more</b>. Before this class existed, a consumer whose own {@code
 * @SpringBootApplication} lives under a different root package than {@code
 * ro.cristivoicu.springbootrestless} (i.e. every real consumer, {@code example} included) had to
 * add {@code @ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")} by hand to get
 * {@link RestlessRegistrar} (or anything else here) into their context at all - undocumented
 * unless read straight out of {@code ExampleApplication}'s own javadoc, and pulling in every
 * {@code @Component} under that package tree, not just the ones actually wanted.
 * <p>
 * Every bean here is {@code @ConditionalOnMissingBean}: a consumer that wants to replace one
 * (a custom {@link RestlessEmbedResolver}, a different {@link RestlessExceptionHandler}, ...)
 * still can, by simply declaring their own bean of that type - this class only ever fills a gap,
 * never overrides an explicit choice, same "default, not mandate" spirit as everything else this
 * framework does.
 */
@AutoConfiguration
@EnableConfigurationProperties(RestlessProperties.class)
public class RestlessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestlessEmbedResolver restlessEmbedResolver(ApplicationContext applicationContext) {
        return new RestlessEmbedResolver(applicationContext);
    }

    /**
     * {@code @ConditionalOnClass(MeterRegistry.class)}: Micrometer rides in transitively with
     * {@code spring-boot-starter-actuator}, an {@code optional} dependency of {@code app} - a
     * consumer with neither on their classpath gets no bean here at all (every {@code
     * RestlessResourceHandler} falls back to {@link RestlessAuthorizationMetrics#NONE} instead),
     * not a {@code ClassNotFoundException}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    public RestlessAuthorizationMetrics restlessAuthorizationMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        return new RestlessAuthorizationMetrics(registryProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestlessExceptionHandler restlessExceptionHandler() {
        return new RestlessExceptionHandler();
    }

    /**
     * {@code @ConditionalOnClass(GlobalOpenApiCustomizer.class)}: {@code app} compiles against
     * springdoc as an {@code optional} Maven dependency specifically so this bean can exist - a
     * consumer with no springdoc on their own classpath gets no bean here at all, not a {@code
     * ClassNotFoundException}. See {@link RestlessOpenApiCustomizer}'s own javadoc for what it does.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(GlobalOpenApiCustomizer.class)
    public RestlessOpenApiCustomizer restlessOpenApiCustomizer(ApplicationContext applicationContext) {
        return new RestlessOpenApiCustomizer(applicationContext);
    }

    /**
     * The bean that actually does the work - discovers every {@code @RestlessResource} bean at
     * startup and registers its routes. Depends on every bean above (Spring resolves them by
     * type regardless of which method here produced them, or a consumer's own override), plus
     * infrastructure Spring Boot/Spring MVC/Spring Data JPA already auto-configure on their own
     * ({@link RequestMappingHandlerMapping}, {@link ObjectMapper}, {@link ConversionService},
     * {@link Validator}, {@link PlatformTransactionManager}).
     */
    @Bean
    @ConditionalOnMissingBean
    public RestlessRegistrar restlessRegistrar(ApplicationContext applicationContext,
                                                RequestMappingHandlerMapping requestMappingHandlerMapping,
                                                ObjectMapper objectMapper,
                                                ConversionService conversionService,
                                                Validator validator,
                                                RestlessEmbedResolver embedResolver,
                                                PlatformTransactionManager transactionManager,
                                                ObjectProvider<RestlessAuthorizationMetrics> metricsProvider,
                                                RestlessProperties properties) {
        return new RestlessRegistrar(applicationContext, requestMappingHandlerMapping, objectMapper,
                conversionService, validator, embedResolver, transactionManager, metricsProvider, properties);
    }
}
