package net.modgarden.backend.util;

import org.jetbrains.annotations.Nullable;

/// A final value that is lazily initialized.
public final class LazyValue<T> {
	@Nullable
	private T value;

	private LazyValue() {
	}

	public static <T> LazyValue<T> of() {
		return new LazyValue<>();
	}

	public <X extends Throwable> T getOrCreate(FallibleSupplier<T, X> supplier) throws X {
		if (this.value == null) {
			this.value = supplier.get();
		}

		return this.value;
	}

	public <X extends Throwable> void ifPresent(FallibleConsumer<T, X> consumer) throws X {
		if (this.value != null) {
			consumer.accept(this.value);
		}
	}
}
