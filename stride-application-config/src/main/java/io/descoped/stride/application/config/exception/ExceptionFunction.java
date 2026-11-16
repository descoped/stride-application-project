package io.descoped.stride.application.config.exception;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lambda to wrap hard exception.
 * <p>
 * Example:
 * <pre>{@code
 * ExceptionFunction.call(() -> somethingThatThrowsAnException)
 *
 * return (Class<R>) Optional.of("Foo.class").map((ExceptionFunction<String, Class<?>>) Class::forName)
 * return (Class<R>) Optional.of("Foo.class").map(ExceptionFunction.call(() -> Class::forName))
 * return Optional.of("Foo.class").map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s)))
 * }</pre>
 *
 * @param <T> the argument type
 * @param <R> the result type
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
