package net.modgarden.backend.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public interface HandleFinder {
	static Optional<MethodHandle> findHandle(HandleFinder finder) {
		try {
			return Optional.of(finder.find(MethodHandles.lookup()));
		} catch (NoSuchMethodException | NoSuchFieldException | NullPointerException | IllegalAccessException e) {
			return Optional.empty();
		}
	}

	MethodHandle find(MethodHandles.Lookup lookup) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException;
}
