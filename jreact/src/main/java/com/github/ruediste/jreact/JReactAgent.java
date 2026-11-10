package com.github.ruediste.jreact;

import static net.bytebuddy.matcher.ElementMatchers.any;

import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    public static final ThreadLocal<Boolean> ALLOW_BODY = ThreadLocal.withInitial(() -> false);
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
        String typeName = type.getName();
        if (typeName.startsWith("java.") || typeName.startsWith("javax.") || typeName.startsWith("jdk.")
                || typeName.startsWith("sun.") || typeName.startsWith("com.sun.")
                || typeName.startsWith("net.bytebuddy.") || typeName.startsWith("com.antepod."))
            return false;
        if (typeName.startsWith("com.github.ruediste.jreact.")
                && !typeName.startsWith("com.github.ruediste.jreact.storybook."))
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

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter(@Advice.Origin Method method, @Advice.AllArguments Object[] arguments,
                @Advice.This(optional = true) Object self) {
            if (ALLOW_BODY.get()) {
                ALLOW_BODY.remove();
                return false;
            }
            Object[] properties = arguments.clone();
            Ui.component(method, properties, new ComponentBody(method, self, arguments));
            return true;
        }
    }

    public static final class ComponentBody implements Runnable {
        private final Method method;
        private final Object self;
        private final Object[] arguments;

        public ComponentBody(Method method, Object self, Object[] arguments) {
            this.method = method;
            this.self = self;
            this.arguments = arguments;
        }

        @Override
        public void run() {
            JReactAgent.invokeBody(method, self, arguments);
        }
    }

    public static void invokeBody(Method method, Object self, Object[] arguments) {
        ALLOW_BODY.set(true);
        try {
            method.setAccessible(true);
            method.invoke(self, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime)
                throw runtime;
            if (cause instanceof Error error)
                throw error;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        } finally {
            ALLOW_BODY.remove();
        }
    }
}
