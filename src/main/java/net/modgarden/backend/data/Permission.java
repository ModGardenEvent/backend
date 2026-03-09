package net.modgarden.backend.data;

import static net.modgarden.backend.data.PermissionScope.ALL;
import static net.modgarden.backend.data.PermissionScope.PROJECT;
import static net.modgarden.backend.data.PermissionScope.USER;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

// TODO: Add more user permissions for stuff.
public enum Permission {
	/// Signifies that this user has every permission.
	/// Do not give this out unless it is absolutely necessary for an individual team member to receive this.
	ADMINISTRATOR(0x1, "administrator", ALL),
	/// Create, edit, and manage roles for users or projects.
	MANAGE_ROLES(0x2, "manage_roles", ALL),
	/// Create, edit, and hide events.
	EDIT_EVENT(0x4, "edit_event", USER),
	/// Edit others' profiles and punish users.
	MODERATE_USERS(0x8, "moderate_users", USER),
	/// Edit your own profile.
	EDIT_PROFILE(0x10, "edit_profile", USER),
	/// Edit this project.
	EDIT_PROJECT(0x20, "edit_project", ALL),
	/// Join, participate, and submit projects in events.
	PARTICIPATE(0x40, "participate", USER),
	/// Upload files to the CDN.
	UPLOAD_TO_CDN(0x80, "upload_to_cdn", USER),
	/// List, modify, and delete files in the CDN.
	MANAGE_CDN(0x100, "manage_cdn", USER),
	/// Generate and delete API keys on behalf of this user or project.
	MODIFY_API_KEY(0x200, "modify_api_key", ALL),;

	/// The default permissions that all users have.
	///
	/// At some point, we're going to switch to user roles,
	/// but for now, users have inherent, default permissions.
	public static final Permissions DEFAULT_USER_PERMISSIONS = new Permissions(
			EDIT_PROFILE,
			PARTICIPATE
	);

	public static final Codec<Permission> CODEC = Codec.STRING.flatXmap(string -> {
		try {
			return DataResult.success(Permission.valueOf(string));
		} catch (IllegalArgumentException ex) {
			return DataResult.error(() -> "Could not find permission '" + string + "'");
		}
	}, permission -> DataResult.success(permission.name));
	public static final Codec<List<Permission>> GLOBAL_LIST_CODEC = Codec.withAlternative(CODEC.listOf(), Codec.STRING.xmap(string -> fromLongString(string,
			USER
	), Permission::toLongString));
	public static final Codec<List<Permission>> PROJECT_LIST_CODEC = Codec.withAlternative(CODEC.listOf(), Codec.STRING.xmap(string -> fromLongString(string, PROJECT), Permission::toLongString));
	public static final Codec<Permissions> PERMISSIONS_CODEC = Codec.LONG.xmap(Permissions::new, Permissions::bits);
	public static final Codec<Permissions> STRING_PERMISSIONS_CODEC = Codec.STRING.xmap(Permissions::new, Permissions::toString);

	private final long bit;
	private final String name;
	private final PermissionScope kind;

	Permission(int bit, String name, PermissionScope kind) {
		this.bit = bit;
		this.name = name;
		this.kind = kind;
	}

	public static List<Permission> fromLong(long value, PermissionScope kind) {
		List<Permission> permissions = new ArrayList<>();
		for (Permission permission : Permission.values(kind)) {
			if (hasPermissionRaw(value, permission)) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	public static List<Permission> fromLongString(String value, PermissionScope kind) {
		return fromLong(Long.parseLong(value), kind);
	}

	public static long toLong(List<Permission> permissions) {
		long value = 0;
		for (Permission permission : permissions) {
			value = grantPermission(value, permission);
		}
		return value;
	}

	public static String toLongString(List<Permission> permissions) {
		return Long.toString(toLong(permissions));
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

	private static List<Permission> values(PermissionScope kind) {
		List<Permission> permissions = new ArrayList<>();
		for (Permission permission : Permission.values()) {
			if (permission.kind == ALL || permission.kind == kind) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	public long getBit() {
		return this.bit;
	}

	public String getName() {
		return name;
	}
}
