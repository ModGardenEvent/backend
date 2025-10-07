package net.modgarden.backend.endpoint.v2.auth;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import net.modgarden.backend.util.UuidUtils;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.util.UUID;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/auth/api_key/{uuid}")
public final class DeleteKeyEndpoint extends AuthEndpoint {
	public DeleteKeyEndpoint() {
		super("api_key/{uuid}", PermissionScope.ALL, false);
	}

	@Override
	public void handle(
			@NotNull Context ctx,
			String userId,
			Permissions userPermissions
	) throws Exception {
		if (!this.requirePermissions(ctx, userPermissions, Permission.MODIFY_API_KEY)) return;

		UUID uuid = UUID.fromString(ctx.pathParam("uuid"));

		try (
				var connection = this.getDatabaseConnection();
				var apiKeyScopeStatement = connection.prepareStatement("SELECT scope, project_id FROM api_key_scopes WHERE uuid = ?");
				var deleteApiKeyStatement = connection.prepareStatement("DELETE FROM api_keys WHERE uuid = ?")
		) {
			apiKeyScopeStatement.setBytes(1, UuidUtils.toBytes(uuid));
			ResultSet apiKeyScopeResult = apiKeyScopeStatement.executeQuery();
			if (!apiKeyScopeResult.isBeforeFirst()) {
				return;
			}

			String projectId = apiKeyScopeResult.getString("project_id");
			PermissionScope permissionScope = PermissionScope.fromString(apiKeyScopeResult.getString("scope"));

			if (permissionScope == PermissionScope.PROJECT) {
				Permissions permissions = this.getDatabaseAccess()
						.getProjectPermissions(userId, projectId)
						.unwrap(ctx);
				if (!this.requirePermissions(ctx, permissions, Permission.MODIFY_API_KEY)) return;
			}

			deleteApiKeyStatement.setBytes(1, UuidUtils.toBytes(uuid));
			deleteApiKeyStatement.execute();
		}
	}
}
