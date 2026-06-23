package net.modgarden.backend.endpoint.v2.users;

import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;

public abstract class AuthorizedUsersEndpoint extends AuthorizedEndpoint {
	protected AuthorizedUsersEndpoint(String path) {
		super(2, "users/" + path, PermissionScope.USER);
	}

	protected AuthorizedUsersEndpoint() {
		super(2, "users", PermissionScope.USER);
	}
}
