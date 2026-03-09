package net.modgarden.backend.endpoint.v2.submissions;

import java.sql.SQLException;

import io.javalin.http.Context;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.exception.HypertextException;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/submissions")
public abstract class AuthorizedSubmissionEndpoint extends AuthorizedEndpoint {
	public AuthorizedSubmissionEndpoint(String path, boolean hasBody) {
		super(2, "submissions/" + path, PermissionScope.PROJECT);
	}

	public AuthorizedSubmissionEndpoint() {
		super (2, "submissions", PermissionScope.PROJECT);
	}

	@NotNull
	@Override
	protected abstract String getProjectId(Context ctx) throws SQLException, HypertextException;

	@Override
	public abstract void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;
}
