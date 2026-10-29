package com.logvex.crystal.integration.baritone;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Small, defensive wrapper around {@link java.lang.reflect} for the Baritone bridge.
 *
 * <p>Baritone is not a compile dependency, so the bridge reaches it reflectively. That is the
 * one place reflection is used in the client, and it is kept entirely inside this package.
 * Every helper here fails with a message that names the missing member, so a future Baritone
 * rename surfaces as a clear "unsupported" diagnostic instead of a {@code NoSuchMethodError}
 * or a silent no-op.
 *
 * <p>The helpers never swallow exceptions: callers decide whether a failure is fatal for an
 * action or merely means "capability unavailable".
 */
final class BaritoneReflection {

    private BaritoneReflection() {
    }

    /** Loads a class, or throws {@link BaritoneBridge.BaritoneException} naming it. */
    static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new BaritoneBridge.BaritoneException("Baritone class not found: " + name, e);
        }
    }

    /** True when the class is loadable; used by the availability probe. */
    static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Resolves a public method, or throws a descriptive exception. */
    static Method method(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new BaritoneBridge.BaritoneException(
                    "Baritone method not found: " + owner.getSimpleName() + "." + name, e);
        }
    }

    /** Resolves a public field, or throws a descriptive exception. */
    static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new BaritoneBridge.BaritoneException(
                    "Baritone field not found: " + owner.getSimpleName() + "." + name, e);
        }
    }

    /** Invokes a method, unwrapping the target invocation exception into a descriptive failure. */
    static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new BaritoneBridge.BaritoneException(
                    "Baritone call failed: " + method.getName() + " (" + cause + ")", cause);
        } catch (IllegalAccessException e) {
            throw new BaritoneBridge.BaritoneException("Baritone call not accessible: " + method.getName(), e);
        }
    }

    /** Reads a field, or throws a descriptive failure. */
    static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new BaritoneBridge.BaritoneException("Baritone field not readable: " + field.getName(), e);
        }
    }

    /** Writes a field, or throws a descriptive failure. */
    static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new BaritoneBridge.BaritoneException(
                    "Baritone field not writable: " + field.getName() + " = " + value, e);
        }
    }

    /** True when {@code method} exists on {@code owner} with the given parameters. */
    static boolean hasMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            owner.getMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Unwraps a Baritone {@code Optional} (a real {@link Optional}) to its value or null. */
    static Object optionalOrNull(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return maybeOptional;
    }

    /** The simple class name of a Baritone goal/object, or {@code "unknown"} for null. */
    static String simpleName(Object value) {
        return value == null ? "unknown" : value.getClass().getSimpleName();
    }
}
