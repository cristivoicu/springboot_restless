package ro.cristivoicu.springbootrestless.registry;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.autoconfigure.RestlessProperties;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import ro.cristivoicu.springbootrestless.resource.ResourceMetadata;
import ro.cristivoicu.springbootrestless.resource.RestlessInitContext;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Discovers every {@code @RestlessResource}-annotated bean at startup and registers its HTTP
 * routes dynamically, generalizing what {@code Stage1HardcodedRegistrar} proved by hand for one
 * route on one resource. {@link SmartInitializingSingleton} (not a
 * {@code BeanDefinitionRegistryPostProcessor}, which runs before singletons exist and so can't
 * hold live bean instances; not {@code ApplicationListener<ContextRefreshedEvent>}, which can
 * fire more than once with parent/child contexts). Registered via {@code
 * RestlessAutoConfiguration} (an {@code @Bean}, not component-scanned) - see its javadoc for why:
 * a consumer no longer has to {@code @ComponentScan} this framework's own package to get this
 * bean (or any other infrastructure bean this framework ships) into their context at all.
 */
public class RestlessRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final ObjectMapper objectMapper;
    private final ConversionService conversionService;
    private final Validator validator;
    private final RestlessEmbedResolver embedResolver;
    private final PlatformTransactionManager transactionManager;
    private final RestlessAuthorizationMetrics metrics;
    private final RestlessProperties properties;

    public RestlessRegistrar(ApplicationContext applicationContext,
                              RequestMappingHandlerMapping requestMappingHandlerMapping,
                              ObjectMapper objectMapper,
                              ConversionService conversionService,
                              Validator validator,
                              RestlessEmbedResolver embedResolver,
                              PlatformTransactionManager transactionManager,
                              ObjectProvider<RestlessAuthorizationMetrics> metricsProvider,
                              RestlessProperties properties) {
        this.applicationContext = applicationContext;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
        this.embedResolver = embedResolver;
        this.transactionManager = transactionManager;
        // ObjectProvider, not a direct RestlessAuthorizationMetrics dependency: its own bean
        // registration is @ConditionalOnClass(MeterRegistry.class) - absent that (no
        // Actuator/Micrometer on the consumer's classpath), getIfAvailable() returns null and
        // every resource gets the shared no-op NONE instance instead.
        this.metrics = metricsProvider.getIfAvailable(() -> RestlessAuthorizationMetrics.NONE);
        this.properties = properties == null ? new RestlessProperties() : properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> seenBasePaths = new HashSet<>();

        applicationContext.getBeansWithAnnotation(RestlessResource.class).values()
                .forEach(bean -> registerResource(bean, seenBasePaths));
    }

    private void registerResource(Object bean, Set<String> seenBasePaths) {
        if (!(bean instanceof RestlessResourceHandler<?, ?> resource)) {
            throw new IllegalStateException("@RestlessResource on " + bean.getClass()
                    + " but it does not extend RestlessResourceHandler");
        }

        RestlessResource annotation = AnnotationUtils.findAnnotation(bean.getClass(), RestlessResource.class);
        String basePath = annotation.basePath();
        if (!seenBasePaths.add(basePath)) {
            throw new IllegalStateException("Duplicate @RestlessResource basePath '" + basePath
                    + "' - each resource must be mounted at a distinct base path");
        }
        String version = annotation.version();

        ResourceMetadata metadata = resource.resolveMetadata(basePath, version);
        resource.init(new RestlessInitContext(metadata, objectMapper, conversionService, validator, embedResolver,
                transactionManager, metrics, properties.getList().getMaxSize(), properties.getPage().getMaxSize(),
                properties.getBulk().getMaxSize(), properties.getSoftDelete().isIncludeInSingleRead()));

        // Fail-fast, not fail-open: a resource with no real AuthorizationGuard (still the
        // default-permissive AuthorizationGuard.allowAll()) never gets registered at all unless
        // @RestlessResource(allowAll = true) says that's genuinely intended - see both javadocs.
        // Deliberately after init() (metadata/basePath already resolved, so the message below can
        // name the resource precisely) but before any route is registered - a startup failure,
        // never a route that silently answered every request wide open.
        if (!resource.hasExplicitAuthorizationGuard() && !annotation.allowAll()) {
            throw new IllegalStateException(
                    "@RestlessResource on " + bean.getClass().getName() + " (basePath '" + basePath
                            + "') has no AuthorizationGuard override and allowAll() is false - "
                            + "override getAuthorizationGuard() (or set authorizationGuard on "
                            + "@RestlessEntity), or set allowAll = true if this resource is "
                            + "genuinely meant to be unauthorized.");
        }

        // getEnabledOperations() defaults to ALL_OPERATIONS (today's behavior, unchanged) -
        // overriding it to a subset is how a resource opts out of the routes it doesn't want.
        Set<AuthorizationGuard.Action> enabledOperations = resource.getEnabledOperations();
        RestlessRoutes.FIXED.stream()
                .filter(route -> enabledOperations.contains(route.operation()))
                .forEach(route -> registerRoute(resource, basePath + route.pathSuffix(), route.httpMethod(), route.handlerMethodName(), version));

        // One extra route per named ReadAction, all sharing the single customRead Method -
        // getCustomReadActions() is empty by default, so this is a no-op for most resources.
        resource.getCustomReadActions().keySet().forEach(actionName ->
                registerRoute(resource, basePath + "/actions/" + actionName, RequestMethod.GET, "customRead", version));

        // One extra route per named view, all sharing the single namedView Method - same
        // no-op-by-default shape as custom read actions above, just single-item (by id) instead
        // of a filtered collection.
        resource.getNamedViews().keySet().forEach(viewName ->
                registerRoute(resource, basePath + "/{id}/" + viewName, RequestMethod.GET, "namedView", version));

        // One extra route per named WriteAction, all sharing the single writeAction Method - same
        // no-op-by-default shape as custom read actions/named views above, POST + id-scoped like
        // an update rather than a filtered collection like customRead.
        resource.getCustomWriteActions().keySet().forEach(actionName ->
                registerRoute(resource, basePath + "/{id}/actions/" + actionName, RequestMethod.POST, "writeAction", version));

        // PATCH: entirely opt-in (see getPatchDataSource()'s own javadoc) - empty by default, so
        // this is a no-op for most resources, same spirit as custom read actions above.
        if (resource.getPatchDataSource().isPresent()) {
            registerRoute(resource, basePath + "/{id}", RequestMethod.PATCH, "patch", version);
        }
    }

    private void registerRoute(RestlessResourceHandler<?, ?> resource, String path, RequestMethod httpMethod,
                                String handlerMethodName, String version) {
        try {
            Method handlerMethod = RestlessResourceHandler.class.getMethod(handlerMethodName, HttpServletRequest.class);
            RequestMappingInfo.Builder builder = RequestMappingInfo.paths(path)
                    .methods(httpMethod)
                    .options(requestMappingHandlerMapping.getBuilderConfiguration());
            // Left off entirely (not set to "") when unset, same as an @RequestMapping with no
            // version attribute at all - a route with no version constraint stays reachable
            // regardless of what version a request resolves to.
            if (StringUtils.hasText(version)) {
                builder.version(version);
            }
            requestMappingHandlerMapping.registerMapping(builder.build(), resource, handlerMethod);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("RestlessResourceHandler." + handlerMethodName + " not found", e);
        }
    }
}
