package net.modgarden.backend.endpoint.v2.auth.api_keys;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

import java.util.Optional;
import java.util.UUID;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(DELETE)
@EndpointPath("/v2/auth/api-keys/{uuid}")
public final class DeleteKeyEndpoint extends AuthEndpoint {
	public DeleteKeyEndpoint() {
		super("api-keys/{uuid}", PermissionScope.ALL, false);
	}

	@Override
	public Response onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		UUID uuid = UUID.fromString(ctx.pathParam("uuid"));

		Optional<DatabaseAccess.ApiKeyScope> optionalApiKeyScope = db.getApiKeyScope(uuid);

		if (optionalApiKeyScope.isEmpty()) {
			throw new NotFoundException("No API key with UUID " + uuid + " exists.");
		}

		DatabaseAccess.ApiKeyScope apiKeyScope = optionalApiKeyScope.get();
		String projectId = apiKeyScope.projectId();
		PermissionScope permissionScope = apiKeyScope.scope();

		if (permissionScope == PermissionScope.PROJECT) {
			Permissions permissions = db.getProjectMemberPermissions(userId, projectId);
			this.requireAllPermissions(permissions, Permission.MODIFY_API_KEY);
		}

		db.deleteApiKey(uuid);
		return Response.ok();
	}

	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.MODIFY_API_KEY);
	}
}
