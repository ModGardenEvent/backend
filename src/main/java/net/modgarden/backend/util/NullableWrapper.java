package net.modgarden.backend.util;

import net.modgarden.backend.util.codec.NullableCodec;
import org.jetbrains.annotations.Nullable;

/// Used within {@link NullableCodec} to allow encasing inside an optional.
public record NullableWrapper<T>(@Nullable T value) {
	public static <T> NullableWrapper<T> of(T value) {
		return new NullableWrapper<>(value);
	}

	public static <T> NullableWrapper<T> empty() {
		return new NullableWrapper<>(null);
	}

	public boolean isPresent() {
		return value != null;
	}

	public boolean isEmpty() {
		return value == null;
	}
}
