package net.modgarden.backend.util;

/// Performs an action based on a value of [T].
@FunctionalInterface
public interface FallibleConsumer<T, X extends Throwable> {
	void accept(T t) throws X;
}
