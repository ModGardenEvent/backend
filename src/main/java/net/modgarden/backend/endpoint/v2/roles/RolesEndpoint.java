package net.modgarden.backend.endpoint.v2.roles;

import net.modgarden.backend.endpoint.Endpoint;

public abstract class RolesEndpoint extends Endpoint {
	protected RolesEndpoint(String path) {
		super(2, "roles/" + path);
	}

	protected RolesEndpoint() {
		super(2, "roles");
	}
}
