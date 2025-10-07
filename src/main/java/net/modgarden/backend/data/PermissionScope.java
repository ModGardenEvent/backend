package net.modgarden.backend.data;

import com.mojang.serialization.Codec;

import java.util.Locale;

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
