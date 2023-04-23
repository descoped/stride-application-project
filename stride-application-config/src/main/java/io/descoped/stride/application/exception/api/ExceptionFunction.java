package io.descoped.stride.application.exception.api;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lambda to wrap hard exception.
 * <p>
 * Example:
 * ExceptionFunction.call(() -> somethingThatThrowsAnException)
 * <p>
 * return (Class<R>) Optional.of("Foo.class").map((ExceptionFunction<String, Class<?>>) Class::forName)
 * return (Class<R>) Optional.of("Foo.class").map(ExceptionFunction.call(() -> Class::forName))
 * return Optional.of("Foo.class").map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s)))
 *
 * @param <T>
 * @param <R>
 */

@FunctionalInterface
public interface ExceptionFunction<T, R> extends Function<T, R> {

    @Override
    default R apply(T t) {
        try {
            return applyThrows(t);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    R applyThrows(T t) throws Exception;

    static <T, R> ExceptionFunction<T, R> call(Supplier<ExceptionFunction<T, R>> callback) {
        return callback.get();
    }
}
