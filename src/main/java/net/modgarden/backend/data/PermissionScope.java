package net.modgarden.backend.data;

import java.util.Locale;

import com.mojang.serialization.Codec;

public enum PermissionScope {
	ALL,
	USER,
	PROJECT,;

	public static final Codec<PermissionScope> CODEC = Codec.STRING.xmap(
			PermissionScope::fromString,
			scope -> scope.name().toLowerCase(Locale.ROOT)
	);

	public static PermissionScope fromString(String string) {
		return valueOf(string.toUpperCase(Locale.ROOT));
	}
}
