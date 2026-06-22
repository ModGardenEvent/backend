package net.modgarden.backend.data.permission;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/// A bitfield of permissions that uses the [Permission] system.
///
/// Note that once value classes come out, this class will become a value class.
public record Permissions(long bits) {
	public Permissions(Permission... permissions) {
		this(Permission.toLong(List.of(permissions)));
	}

	public Permissions(String bitsString) {
		this(Long.parseLong(bitsString));
	}

	public Permissions grantPermissions(Permissions permissions) {
		return new Permissions(this.bits | permissions.bits);
	}

	public Permissions grantPermissions(Permission... permissions) {
		return this.grantPermissions(new Permissions(permissions));
	}

	public Permissions revokePermissions(Permissions permissions) {
		return new Permissions(this.bits ^ permissions.bits);
	}

	public Permissions revokePermissions(Permission... permissions) {
		return this.revokePermissions(new Permissions(permissions));
	}

	public boolean hasPermissions(Permissions required) {
		boolean hasPermissions = (required.bits & this.bits) == required.bits;
		boolean hasAdministrator = hasAdministrator(this.bits);
		return hasAdministrator || hasPermissions;
	}

	public boolean hasAnyPermissions(Permissions required) {
		boolean hasPermissions = (required.bits & this.bits) > 0;
		boolean hasAdministrator = hasAdministrator(this.bits);
		return hasAdministrator || hasPermissions;
	}

	public boolean hasPermissions(Permission... permissions) {
		return this.hasPermissions(new Permissions(permissions));
	}

	public boolean hasAnyPermissions(Permission... permissions) {
		return this.hasAnyPermissions(new Permissions(permissions));
	}

	/// Only allows permissions in [#bits] and ignores all other permissions.
	public Permissions restrictTo(long bits) {
		return new Permissions(this.bits & bits);
	}

	private static boolean hasAdministrator(long bits) {
		return (bits & Permission.ADMINISTRATOR.getBit()) != 0;
	}

	@NotNull
	public String toString() {
		StringBuilder builder = new StringBuilder();
		long bits = this.bits;
		builder.append('[');

		for (int i = 0; i < Long.bitCount(this.bits); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			Permission permission = Permission.values()[Long.numberOfTrailingZeros(bits)];
			builder.append(permission.getName());
			bits = bits ^ Long.lowestOneBit(bits);
		}

		builder.append(']');
		return builder.toString();
	}

	public String toLongString() {
		return Long.toString(this.bits);
	}
}
