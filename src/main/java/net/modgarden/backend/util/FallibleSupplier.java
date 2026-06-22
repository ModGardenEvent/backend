package net.modgarden.backend.util;

/// Returns instances of [T].
@FunctionalInterface
public interface FallibleSupplier<T, X extends Throwable> {
	T get() throws X;
}
