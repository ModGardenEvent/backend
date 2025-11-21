package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/project")
public abstract class AuthorizedProjectEndpoint extends AuthorizedEndpoint {
	public AuthorizedProjectEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(2, "project/" + path, permissionScope, hasBody);
	}

	@Override
	public abstract void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;
}
