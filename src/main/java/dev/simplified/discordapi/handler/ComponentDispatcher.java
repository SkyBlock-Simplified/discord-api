package dev.simplified.discordapi.handler;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.context.EternalBuildContext;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.listener.Component;
import dev.simplified.discordapi.listener.Eternal;
import dev.simplified.discordapi.listener.EternalComponentListener;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Central registry for {@link Component @Component}-annotated component
 * interaction handlers. Constructed once per bot during
 * {@link DiscordBot#connect()}, AFTER the {@link CommandHandler} (which it
 * reads from) and AFTER classpath scanning has produced the set of
 * {@link EternalComponentListener} subclasses.
 *
 * <p>
 * Discovery walks each command and listener instance's declared methods,
 * validates the annotation contract, and stores a {@link MethodHandle} per
 * route so dispatch is reflection-free at runtime. Validation errors and
 * duplicate-id conflicts are logged via {@link DiscordReference#getLog()}.
 *
 * <p>
 * Routes are split into two registries:
 * <ul>
 *   <li><b>exact</b> - an O(1) map keyed by literal {@code customId}</li>
 *   <li><b>regex</b> - an ordered list of pre-compiled {@link Pattern}s</li>
 * </ul>
 * Lookup tries exact first; only on a miss does it scan the regex list. A
 * single regex match dispatches; multiple regex matches log an error and
 * report {@link MatchedRoute.Kind#AMBIGUOUS AMBIGUOUS} so the caller can drop
 * the interaction.
 *
 * @see Component
 * @see EternalComponentListener
 */
public final class ComponentDispatcher extends DiscordReference {

    /** Routing entry for a {@link Component @Component}-annotated method. */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ComponentRoute {

        /** The instance hosting the annotated method. */
        private final @NotNull Object instance;

        /** The class hosting the annotated method - the dispatching owner. */
        private final @NotNull Class<?> ownerClass;

        /** Reflection-free invocation handle for the method. */
        private final @NotNull MethodHandle methodHandle;

        /** The expected static parameter type (a {@link ComponentContext} subtype). */
        private final @NotNull Class<?> expectedContextType;

        /** Per-route cache time-to-live override in seconds, or {@code 0} for none. */
        private final long cacheTtl;

    }

    /**
     * Routing entry for a {@link Component @Component}-annotated method whose
     * {@link Component#regex() regex} is {@code true}, carrying the compiled
     * {@link Pattern} alongside the standard {@link ComponentRoute} fields.
     */
    @Getter
    public static final class RegexRoute extends ComponentRoute {

        /** The pre-compiled custom id pattern. */
        private final @NotNull Pattern pattern;

        /** The original pattern string from {@link Component#value()}. */
        private final @NotNull String patternString;

        private RegexRoute(
            @NotNull Object instance,
            @NotNull Class<?> ownerClass,
            @NotNull MethodHandle methodHandle,
            @NotNull Class<?> expectedContextType,
            long cacheTtl,
            @NotNull Pattern pattern,
            @NotNull String patternString
        ) {
            super(instance, ownerClass, methodHandle, expectedContextType, cacheTtl);
            this.pattern = pattern;
            this.patternString = patternString;
        }

    }

    /** Result of a {@link #findRoute(String) findRoute} lookup. */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class MatchedRoute {

        /** Discriminator for the kind of match a {@link MatchedRoute} represents. */
        public enum Kind {

            /** The custom id matched a literal {@code customId} entry exactly. */
            EXACT,

            /** The custom id matched exactly one regex route. */
            REGEX,

            /** The custom id matched two or more regex routes; caller must drop the interaction. */
            AMBIGUOUS

        }

        /** The kind of match found. */
        private final @NotNull Kind kind;

        /**
         * The matched route, or empty for {@link Kind#AMBIGUOUS} where there
         * is no single canonical route to report.
         */
        private final @NotNull Optional<ComponentRoute> route;

        private static @NotNull MatchedRoute exact(@NotNull ComponentRoute route) {
            return new MatchedRoute(Kind.EXACT, Optional.of(route));
        }

        private static @NotNull MatchedRoute regex(@NotNull ComponentRoute route) {
            return new MatchedRoute(Kind.REGEX, Optional.of(route));
        }

        private static @NotNull MatchedRoute ambiguous() {
            return new MatchedRoute(Kind.AMBIGUOUS, Optional.empty());
        }

    }

    /** Routing entry for an {@link Eternal @Eternal}-annotated response rebuild function. */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class EternalRoute {

        /** The instance hosting the annotated method. */
        private final @NotNull Object instance;

        /** The class hosting the annotated method - the dispatching owner. */
        private final @NotNull Class<?> ownerClass;

        /** Reflection-free invocation handle for the method. */
        private final @NotNull MethodHandle methodHandle;

    }

    /** Loaded {@link EternalComponentListener} instances, in registration order. */
    @Getter private final @NotNull ConcurrentList<EternalComponentListener> loadedListeners;

    /** Map from explicit {@code custom_id} to its routing entry. */
    @Getter private final @NotNull ConcurrentMap<String, ComponentRoute> exactRoutes = Concurrent.newMap();

    /** Ordered list of regex routes, scanned only on exact-route miss. */
    @Getter private final @NotNull ConcurrentList<RegexRoute> regexRoutes = Concurrent.newList();

    /** Map from an {@link Eternal @Eternal} key to its rebuild-function route. */
    @Getter private final @NotNull ConcurrentMap<String, EternalRoute> eternalRoutes = Concurrent.newMap();

    /**
     * Constructs the registry by scanning the given commands and listeners
     * for {@link Component @Component}-annotated methods.
     *
     * @param discordBot the bot this dispatcher belongs to
     * @param loadedCommands command instances loaded by {@link CommandHandler}
     * @param listenerClasses persistent listener subclasses discovered via classpath scan
     */
    @SuppressWarnings("rawtypes")
    public ComponentDispatcher(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentList<DiscordCommand> loadedCommands,
        @NotNull ConcurrentSet<Class<? extends EternalComponentListener>> listenerClasses
    ) {
        super(discordBot);

        this.getLog().info("Loading Component Dispatcher");
        this.loadedListeners = listenerClasses.stream()
            .map(listenerClass -> (EternalComponentListener) new Reflection<>(listenerClass).newInstance(discordBot))
            .collect(Concurrent.toList());

        loadedCommands.forEach(command -> this.scanInstance(command, command.getClass()));
        this.loadedListeners.forEach(listener -> this.scanInstance(listener, listener.getClass()));

        this.getLog().info(
            "Discovered {} component routes ({} exact, {} regex), {} eternal builders",
            this.exactRoutes.size() + this.regexRoutes.size(),
            this.exactRoutes.size(),
            this.regexRoutes.size(),
            this.eternalRoutes.size()
        );

        this.warnOverlappingRoutes();
    }

    /**
     * Finds the registered {@link Eternal @Eternal} rebuild function for the given key.
     *
     * @param builderKey the stable eternal key
     * @return the matching builder route, or empty if none is registered
     */
    public @NotNull Optional<EternalRoute> findEternalBuilder(@NotNull String builderKey) {
        return Optional.ofNullable(this.eternalRoutes.get(builderKey));
    }

    /**
     * Invokes an eternal rebuild function, returning the freshly built response. The framework
     * stamps identity, the eternal marker, and the navigation coordinate around this call.
     *
     * @param route the eternal builder route to invoke
     * @param context the build context carrying the payload and originating metadata
     * @return the rebuilt response
     */
    public @NotNull Response invokeEternalBuilder(@NotNull EternalRoute route, @NotNull EternalBuildContext context) {
        try {
            return (Response) route.getMethodHandle().invoke(route.getInstance(), context);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    /**
     * Finds the route matching the given custom id. Exact matches always win;
     * regex matches are evaluated only on exact-route miss.
     *
     * @param customId the Discord component {@code custom_id}
     * @return the matched route descriptor, or empty if no route matches
     */
    public @NotNull Optional<MatchedRoute> findRoute(@NotNull String customId) {
        ComponentRoute exact = this.exactRoutes.get(customId);
        if (exact != null)
            return Optional.of(MatchedRoute.exact(exact));

        ConcurrentList<RegexRoute> matches = this.regexRoutes.stream()
            .filter(route -> route.getPattern().matcher(customId).matches())
            .collect(Concurrent.toList());

        if (matches.isEmpty())
            return Optional.empty();

        if (matches.size() > 1) {
            this.getLog().error(
                "Ambiguous regex match for customId '{}': {} routes matched ({})",
                customId,
                matches.size(),
                matches.stream()
                    .map(route -> route.getOwnerClass().getName() + " [" + route.getPatternString() + "]")
                    .toList()
            );
            return Optional.of(MatchedRoute.ambiguous());
        }

        return Optional.of(MatchedRoute.regex(matches.getFirst()));
    }

    /**
     * Reflectively scans the given instance's declared methods for
     * {@link Component @Component} and {@link Eternal @Eternal} annotations,
     * validating signatures and registering routes.
     */
    private void scanInstance(@NotNull Object instance, @NotNull Class<?> ownerClass) {
        for (Method method : ownerClass.getDeclaredMethods()) {
            Component componentAnnotation = method.getAnnotation(Component.class);
            if (componentAnnotation != null)
                this.registerComponentRoute(instance, ownerClass, method, componentAnnotation);

            Eternal eternalAnnotation = method.getAnnotation(Eternal.class);
            if (eternalAnnotation != null)
                this.registerEternalRoute(instance, ownerClass, method, eternalAnnotation);
        }
    }

    /** Validates and registers an {@code @Eternal}-annotated rebuild function. */
    private void registerEternalRoute(@NotNull Object instance, @NotNull Class<?> ownerClass, @NotNull Method method, @NotNull Eternal annotation) {
        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].isAssignableFrom(EternalBuildContext.class)) {
            this.getLog().warn(
                "@Eternal method '{}#{}' must declare exactly one parameter assignable from EternalBuildContext, ignoring",
                ownerClass.getName(),
                method.getName()
            );
            return;
        }

        if (!Response.class.isAssignableFrom(method.getReturnType())) {
            this.getLog().warn(
                "@Eternal method '{}#{}' must return a Response, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                method.getReturnType().getName()
            );
            return;
        }

        MethodHandle handle;
        try {
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException ex) {
            this.getLog().error(
                "@Eternal method '{}#{}' could not be unreflected, ignoring",
                ownerClass.getName(),
                method.getName(),
                ex
            );
            return;
        }

        String builderKey = annotation.value();
        if (this.eternalRoutes.containsKey(builderKey)) {
            EternalRoute existing = this.eternalRoutes.get(builderKey);
            this.getLog().warn(
                "@Eternal key '{}' on '{}#{}' conflicts with '{}', ignoring",
                builderKey,
                ownerClass.getName(),
                method.getName(),
                existing.getOwnerClass().getName()
            );
            return;
        }

        this.eternalRoutes.put(builderKey, new EternalRoute(instance, ownerClass, handle));
    }

    /** Validates and registers a {@code @Component}-annotated method. */
    private void registerComponentRoute(@NotNull Object instance, @NotNull Class<?> ownerClass, @NotNull Method method, @NotNull Component annotation) {
        if (method.getParameterCount() != 1) {
            this.getLog().warn(
                "@Component method '{}#{}' must declare exactly one parameter, ignoring",
                ownerClass.getName(),
                method.getName()
            );
            return;
        }

        Class<?> paramType = method.getParameterTypes()[0];
        if (!ComponentContext.class.isAssignableFrom(paramType)) {
            this.getLog().warn(
                "@Component method '{}#{}' parameter must be a ComponentContext subtype, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                paramType.getName()
            );
            return;
        }

        if (!Publisher.class.isAssignableFrom(method.getReturnType())) {
            this.getLog().warn(
                "@Component method '{}#{}' must return a Publisher<Void>, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                method.getReturnType().getName()
            );
            return;
        }

        MethodHandle handle;
        try {
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException ex) {
            this.getLog().error(
                "@Component method '{}#{}' could not be unreflected, ignoring",
                ownerClass.getName(),
                method.getName(),
                ex
            );
            return;
        }

        if (annotation.regex()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(annotation.value());
            } catch (PatternSyntaxException ex) {
                this.getLog().error(
                    "@Component method '{}#{}' has invalid regex '{}', ignoring",
                    ownerClass.getName(),
                    method.getName(),
                    annotation.value(),
                    ex
                );
                return;
            }

            this.regexRoutes.add(new RegexRoute(
                instance,
                ownerClass,
                handle,
                paramType,
                annotation.cacheTtl(),
                pattern,
                annotation.value()
            ));
            return;
        }

        String customId = annotation.value();
        if (this.exactRoutes.containsKey(customId)) {
            ComponentRoute existing = this.exactRoutes.get(customId);
            this.getLog().warn(
                "@Component custom id '{}' on '{}#{}' conflicts with '{}', ignoring",
                customId,
                ownerClass.getName(),
                method.getName(),
                existing.getOwnerClass().getName()
            );
            return;
        }

        this.exactRoutes.put(customId, new ComponentRoute(
            instance,
            ownerClass,
            handle,
            paramType,
            annotation.cacheTtl()
        ));
    }

    /**
     * Logs a warning for each registered regex pattern that would shadow an
     * existing exact id, alerting the user that the regex never gets a chance
     * to fire for that id (exact match always wins).
     */
    private void warnOverlappingRoutes() {
        for (RegexRoute regex : this.regexRoutes) {
            for (String exactId : this.exactRoutes.keySet()) {
                if (regex.getPattern().matcher(exactId).matches()) {
                    this.getLog().warn(
                        "@Component regex '{}' on '{}' shadows exact id '{}' (exact match always wins)",
                        regex.getPatternString(),
                        regex.getOwnerClass().getName(),
                        exactId
                    );
                }
            }
        }
    }

}
