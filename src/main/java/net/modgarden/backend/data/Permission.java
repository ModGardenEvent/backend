package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

// TODO: Add more user permissions for stuff.
public enum Permission {
	/**
	 * Signifies that this user has every permission.
	 * Do not give this out unless it is absolutely necessary for an individual team member to receive this.
	 */
	ADMINISTRATOR(0x1, "administrator"),
	EDIT_PROFILES(0x2, "edit_profiles"),
	MODERATE_USERS(0x4, "moderate_users"),
	EDIT_PROJECTS(0x8, "edit_projects"),
	MODERATE_PROJECTS(0x10, "moderate_projects"),;

	public static final Codec<Permission> CODEC = Codec.STRING.flatXmap(string -> {
		try {
			return DataResult.success(Permission.valueOf(string));
		} catch (IllegalArgumentException ex) {
			return DataResult.error(() -> "Could not find permission '" + string + "'");
		}
	}, permission -> DataResult.success(permission.name));
	public static final Codec<List<Permission>> LIST_CODEC = Codec.withAlternative(CODEC.listOf(), Codec.LONG.xmap(Permission::fromLong, Permission::toLong));

	private final long bit;
	private final String name;

	Permission(int bit, String name) {
		this.bit = bit;
		this.name = name;
	}

	public static List<Permission> fromLong(long value) {
		List<Permission> permissions = new ArrayList<>();
		for (Permission permission : Permission.values()) {
			if (hasPermissionRaw(value, permission)) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	public static long toLong(List<Permission> permissions) {
		long value = 0;
		for (Permission permission : permissions) {
			value = grantPermission(value, permission);
		}
		return value;
	}

	public static long grantPermission(long previousValue, Permission permission) {
		long newValue = previousValue;
		newValue |= permission.bit;
		return newValue;
	}

	public static long revokePermission(long previousValue, Permission permission) {
		long newValue = previousValue;
		newValue ^= permission.bit;
		return newValue;
	}

	public static boolean hasPermission(long userPermissions, Permission permission) {
		return userPermissions == 1 || hasPermissionRaw(userPermissions, permission);
	}

	private static boolean hasPermissionRaw(long userPermissions, Permission permission) {
		return (userPermissions & permission.bit) != 0;
	}

	public String getName() {
		return name;
	}
}
