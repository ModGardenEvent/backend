package net.modgarden.backend.endpoint.v2.project.member;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.util.Map;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PUT;

@EndpointMethod(PUT)
@EndpointPath("/v2/project/{project_id}/set_permissions")
public class SetPermissionsEndpoint extends AuthorizedProjectEndpoint {
	public SetPermissionsEndpoint() {
		super("{project_id}/set_permissions", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		String projectId = ctx.pathParam("project_id");

		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		try (
				var connection = this.getDatabaseConnection();
				var updateStatement = connection.prepareStatement("""
					UPDATE project_roles
					SET permissions = ?
					WHERE project_id = ? AND user_id = ?
				""")
		) {
			for (Map.Entry<String, Permissions> usersToPermissions : request.usersToPermissions().entrySet()) {
				if (!canModifyUser(ctx, connection, projectId, usersToPermissions.getKey(), scopePermissions)) return;

				updateStatement.setLong(1, usersToPermissions.getValue().bits());
				updateStatement.setString(2, projectId);
				updateStatement.setString(3, usersToPermissions.getKey());
				updateStatement.executeUpdate();
			}
		}
	}

	public record Request(Map<String, Permissions> usersToPermissions) {
		public static final Codec<Request> CODEC = Codec.unboundedMap(User.ID_CODEC, Permission.STRING_PERMISSIONS_CODEC)
				.xmap(Request::new, Request::usersToPermissions);
	}
}
