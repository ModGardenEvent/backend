package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/auth")
public abstract class AuthEndpoint extends AuthorizedEndpoint {
	public AuthEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(2, "auth/" + path, permissionScope);
	}

	@Override
	public abstract Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;
}
