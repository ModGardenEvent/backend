package net.modgarden.backend.data.permission;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/// A predicate that determines whether a set of permissions matches another set of permissions.
public final class PermissionPredicate implements Predicate<Permissions> {
	private final Mode mode;
	private final Permissions permissions;

	private PermissionPredicate(
			Mode mode,
			Permissions permissions
	) {
		this.mode = mode;
		this.permissions = permissions;
	}

	/// Any one of the permissions specified are required.
	///
	/// **Do not use this method when you require only one permission.**
	/// Instead, use [#all(Permission...)].
	public static PermissionPredicate any(Permission... permissions) {
		return new PermissionPredicate(Mode.ANY, new Permissions(permissions));
	}

	/// All permissions specified are required.
	///
	/// Prefer this method when you require only one permission.
	public static PermissionPredicate all(Permission... permissions) {
		return new PermissionPredicate(Mode.ALL, new Permissions(permissions));
	}

	@Override
	public boolean test(Permissions permissions) {
		return switch (this.mode) {
			case ANY -> permissions.hasAnyPermissions(this.permissions);
			case ALL -> permissions.hasPermissions(this.permissions);
		};
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (PermissionPredicate) obj;
		return Objects.equals(this.mode, that.mode) && Objects.equals(this.permissions, that.permissions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(mode, permissions);
	}

	@Override
	public String toString() {
		return "PermissionPredicate[" + "mode=" + mode + ", " + "permissions=" + permissions + ']';
	}

	public Mode getMode() {
		return this.mode;
	}

	public Permissions getPermissions() {
		return this.permissions;
	}

	public enum Mode {
		ANY, ALL;


		@Override
		public String toString() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}
}
