package net.modgarden.backend.endpoint.v2.projects;

import java.sql.SQLException;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.HypertextException;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/projects")
public abstract class AuthorizedProjectEndpoint extends AuthorizedEndpoint {
	protected AuthorizedProjectEndpoint(String path, boolean hasBody) {
		this(path, PermissionScope.PROJECT, hasBody);
	}

	protected AuthorizedProjectEndpoint(String path, PermissionScope scope, boolean hasBody) {
		super(2, "projects/" + path, scope);
	}

	protected AuthorizedProjectEndpoint(PermissionScope scope, boolean hasBody) {
		super(2, "projects", scope);
	}

	@Override
	public abstract Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	@NotNull
	@Override
	protected abstract String getProjectId(Context ctx) throws SQLException, HypertextException;
}
