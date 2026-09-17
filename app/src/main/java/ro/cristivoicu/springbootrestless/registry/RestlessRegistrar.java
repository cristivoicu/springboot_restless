package ro.cristivoicu.springbootrestless.registry;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.resource.ResourceMetadata;
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
 * fire more than once with parent/child contexts).
 */
@Component
public class RestlessRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final ObjectMapper objectMapper;
    private final ConversionService conversionService;
    private final Validator validator;
    private final RestlessEmbedResolver embedResolver;
    private final PlatformTransactionManager transactionManager;

    public RestlessRegistrar(ApplicationContext applicationContext,
                              RequestMappingHandlerMapping requestMappingHandlerMapping,
                              ObjectMapper objectMapper,
                              ConversionService conversionService,
                              Validator validator,
                              RestlessEmbedResolver embedResolver,
                              PlatformTransactionManager transactionManager) {
        this.applicationContext = applicationContext;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
        this.embedResolver = embedResolver;
        this.transactionManager = transactionManager;
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

        ResourceMetadata metadata = resource.resolveMetadata(basePath);
        resource.init(metadata, objectMapper, conversionService, validator, embedResolver, transactionManager);

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
