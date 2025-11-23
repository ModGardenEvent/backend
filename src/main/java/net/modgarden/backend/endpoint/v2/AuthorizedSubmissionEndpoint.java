package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;

@EndpointPath("/v2/submission")
public abstract class AuthorizedSubmissionEndpoint extends AuthorizedEndpoint {
	public AuthorizedSubmissionEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(2, "submission/" + path, permissionScope, hasBody);
	}

	@NotNull
	@Override
	protected abstract String getProjectId(Context ctx) throws SQLException;

	@Override
	public abstract void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;
}
