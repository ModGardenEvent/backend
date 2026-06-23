package net.modgarden.backend.endpoint.v2.roles;

import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;

public abstract class AuthorizedRolesEndpoint extends AuthorizedEndpoint {
	protected AuthorizedRolesEndpoint(String path) {
		super(2, "roles/" + path, PermissionScope.USER);
	}

	protected AuthorizedRolesEndpoint() {
		super(2, "roles", PermissionScope.USER);
	}
}
