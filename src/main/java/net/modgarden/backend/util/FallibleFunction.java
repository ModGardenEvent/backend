package net.modgarden.backend.util;

@FunctionalInterface
public interface FallibleFunction<T, R, X extends Throwable> {
	R apply(T t) throws X;
}
