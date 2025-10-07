package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/auth")
public abstract class AuthEndpoint extends AuthorizedEndpoint {
	public AuthEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(2, "auth/" + path, permissionScope, hasBody);
	}

	@Override
	public abstract void handle(@NotNull Context ctx, String userId, Permissions userPermissions) throws Exception;
}
