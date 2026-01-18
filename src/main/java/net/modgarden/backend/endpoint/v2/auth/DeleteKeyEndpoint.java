package net.modgarden.backend.endpoint.v2.auth;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/auth/api_key/{uuid}")
public final class DeleteKeyEndpoint extends AuthEndpoint {
	public DeleteKeyEndpoint() {
		super("api_key/{uuid}", PermissionScope.ALL, false);
	}

	@Override
	public void onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		if (this.requireAllPermissions(ctx, scopePermissions, Permission.MODIFY_API_KEY)) return;

		DatabaseAccess db = DatabaseAccess.get();
		UUID uuid = UUID.fromString(ctx.pathParam("uuid"));

		Optional<DatabaseAccess.ApiKeyScope> optionalApiKeyScope = db.getApiKeyScope(uuid);
		if (optionalApiKeyScope.isEmpty()) {
			return;
		}

		DatabaseAccess.ApiKeyScope apiKeyScope = optionalApiKeyScope.get();
		String projectId = apiKeyScope.projectId();
		PermissionScope permissionScope = apiKeyScope.scope();

		if (permissionScope == PermissionScope.PROJECT) {
			Permissions permissions = db.getProjectMemberPermissions(userId, projectId)
					.unwrap(ctx);
			if (this.requireAllPermissions(ctx, permissions, Permission.MODIFY_API_KEY)) return;
		}

		db.deleteApiKey(uuid);
	}
}
