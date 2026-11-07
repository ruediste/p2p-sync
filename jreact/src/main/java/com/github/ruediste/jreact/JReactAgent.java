package com.github.ruediste.jreact;

import static net.bytebuddy.matcher.ElementMatchers.any;

import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

/** Installs runtime component boundaries for JReact component methods. */
public final class JReactAgent {
    /** Set to {@code true} to require {@link JReactComponent} on each component. */
    public static final String REQUIRE_ANNOTATION_PROPERTY = "jreact.requireComponentAnnotation";

    private static boolean installed;

    private JReactAgent() {
    }

    /**
     * Attaches the agent to the current JVM and transforms eligible methods.
     * Set {@value #REQUIRE_ANNOTATION_PROPERTY} to require an explicit annotation.
     */
    public static synchronized void install() {
        if (installed)
            return;
        boolean requireAnnotation = Boolean.getBoolean(REQUIRE_ANNOTATION_PROPERTY);
        Instrumentation instrumentation = ByteBuddyAgent.install();
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(any())
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(ComponentAdvice.class)
                                .on(method -> shouldTransform(type, method, requireAnnotation))))
                .installOn(instrumentation);
        installed = true;
    }

    static boolean shouldTransform(TypeDescription type, MethodDescription method, boolean requireAnnotation) {
        if (!method.isMethod() || method.isAbstract() || method.isNative() || method.isSynthetic())
            return false;
        if (hasAnnotation(method, NoJReactComponent.class) || hasAnnotation(type, NoJReactComponent.class))
            return false;
        if (hasAnnotation(method, JReactComponent.class) || hasAnnotation(type, JReactComponent.class))
            return true;
        return !requireAnnotation && Character.isUpperCase(method.getName().codePointAt(0));
    }

    private static boolean hasAnnotation(AnnotationSource source, Class<? extends Annotation> annotation) {
        return source.getDeclaredAnnotations().isAnnotationPresent(annotation);
    }

    public static final class ComponentAdvice {
        private ComponentAdvice() {
        }

        @Advice.OnMethodEnter
        public static Ui.ComponentScope enter(@Advice.Origin("#t.#m#d") String identity) {
            return Ui.enterComponent(identity);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter Ui.ComponentScope scope, @Advice.Thrown Throwable failure) {
            scope.close(failure);
        }
    }
}