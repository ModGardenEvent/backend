package net.modgarden.backend.data;

import java.util.List;

/// A bitfield of permissions that uses the [Permission] system.
public final class Permissions {
	private long bits;

	public Permissions(long bits) {
		this.bits = bits;
	}

	public Permissions(Permission... permissions) {
		this.bits = Permission.toLong(List.of(permissions));
	}

	public Permissions(String bitsString) {
		this.bits = Long.parseLong(bitsString);
	}

	public void grantPermission(Permission permission) {
		this.bits = Permission.grantPermission(this.bits, permission);
	}

	public void revokePermission(Permission permission) {
		this.bits = Permission.revokePermission(this.bits, permission);
	}

	public boolean hasPermission(Permission permission) {
		return Permission.hasPermission(this.bits, permission);
	}

	public void and(long bits) {
		this.bits &= bits;
	}

	public long getBits() {
		return this.bits;
	}

	public String toString() {
		return Long.toString(this.bits);
	}
}
