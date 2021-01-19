package xyz.acrylicstyle.connectionListener.util;

import org.jetbrains.annotations.Contract;

import java.lang.reflect.InvocationTargetException;

public class LazyInitVar<T> {
    private final Object o;

    @Contract(pure = true)
    public LazyInitVar(Object o) {
        this.o = o;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        try {
            return (T) o.getClass().getMethod("c").invoke(o); // try c
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            try {
                return (T) o.getClass().getMethod("a").invoke(o); // try a
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
}
