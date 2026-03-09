package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/projects")
public abstract class AuthorizedProjectEndpoint extends AuthorizedEndpoint {
	protected AuthorizedProjectEndpoint(String path, boolean hasBody) {
		this(path, PermissionScope.PROJECT, hasBody);
	}

	protected AuthorizedProjectEndpoint(String path, PermissionScope scope, boolean hasBody) {
		super(2, "projects/" + path, scope, hasBody);
	}

	protected AuthorizedProjectEndpoint(PermissionScope scope, boolean hasBody) {
		super(2, "projects", scope, hasBody);
	}

	@Override
	public abstract void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	@NotNull
	@Override
	protected abstract String getProjectId(Context ctx);

	protected static boolean userCannotModifyMember(
			Context ctx,
			String projectId,
			String memberUserIdToModify,
			Permissions selfPermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		HypertextResult<Void> result = db.canUserModifyMember(projectId, memberUserIdToModify, selfPermissions);
		result.unwrap(ctx);
		return !result.isSuccess();
	}
}
