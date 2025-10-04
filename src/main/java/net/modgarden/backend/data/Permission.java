package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

import static net.modgarden.backend.data.PermissionKind.*;

// TODO: Add more user permissions for stuff.
public enum Permission {
	/**
	 * Signifies that this user has every permission.
	 * Do not give this out unless it is absolutely necessary for an individual team member to receive this.
	 */
	ADMINISTRATOR(0x1, "administrator", ALL),
	EDIT_PROFILES(0x2, "edit_profiles", GLOBAL),
	MODERATE_USERS(0x4, "moderate_users", GLOBAL),
	EDIT_PROJECTS(0x8, "edit_projects", GLOBAL),
	MODERATE_PROJECTS(0x10, "moderate_projects", GLOBAL),
	UPLOAD_TO_CDN(0x20, "upload_to_cdn", GLOBAL),;

	public static final Codec<Permission> CODEC = Codec.STRING.flatXmap(string -> {
		try {
			return DataResult.success(Permission.valueOf(string));
		} catch (IllegalArgumentException ex) {
			return DataResult.error(() -> "Could not find permission '" + string + "'");
		}
	}, permission -> DataResult.success(permission.name));
	public static final Codec<List<Permission>> GLOBAL_LIST_CODEC = Codec.withAlternative(CODEC.listOf(), Codec.LONG.xmap(l -> fromLong(l, GLOBAL), Permission::toLong));
	public static final Codec<List<Permission>> PROJECT_LIST_CODEC = Codec.withAlternative(CODEC.listOf(), Codec.LONG.xmap(l -> fromLong(l, PROJECT), Permission::toLong));

	private final long bit;
	private final String name;
	private final PermissionKind kind;

	Permission(int bit, String name, PermissionKind kind) {
		this.bit = bit;
		this.name = name;
		this.kind = kind;
	}

	public static List<Permission> fromLong(long value, PermissionKind kind) {
		List<Permission> permissions = new ArrayList<>();
		for (Permission permission : Permission.values(kind)) {
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

	private static List<Permission> values(PermissionKind kind) {
		List<Permission> permissions = new ArrayList<>();
		for (Permission permission : Permission.values()) {
			if (permission.kind == ALL || permission.kind == kind) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	public String getName() {
		return name;
	}
}
