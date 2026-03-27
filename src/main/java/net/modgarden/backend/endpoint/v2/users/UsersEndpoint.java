package net.modgarden.backend.endpoint.v2.users;

import net.modgarden.backend.endpoint.Endpoint;

public abstract class UsersEndpoint extends Endpoint {
	protected UsersEndpoint(String path) {
		super(2, "users/" + path);
	}

	protected UsersEndpoint() {
		super(2, "users");
	}
}
