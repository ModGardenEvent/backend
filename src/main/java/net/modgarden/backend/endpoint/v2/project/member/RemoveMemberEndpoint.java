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

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/project/{project_id}/remove_member")
public class RemoveMemberEndpoint extends AuthorizedProjectEndpoint {
	public RemoveMemberEndpoint() {
		super("{project_id}/remove_member", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode

		String projectId = ctx.pathParam("project_id");
		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null || !request.userId().equals(userId) && this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		try (
				var connection = this.getDatabaseConnection();
				var memberPermissionsStatement = connection.prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""");
				var permissionCountStatement = connection.prepareStatement("""
					SELECT COUNT(*)
					FROM project_roles
					WHERE project_id = ? AND has_permissions(permissions, 256)
				""");
				var deleteStatement = connection.prepareStatement("""
					DELETE FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")
		) {
			memberPermissionsStatement.setString(1, projectId);
			memberPermissionsStatement.setString(2, request.userId());
			ResultSet memberPermissionsResult = memberPermissionsStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// If a non-administrator attempts to remove an administrator, return.
			if (!canModifyUser(ctx, connection, projectId, request.userId(), scopePermissions)) return;

			boolean memberCanEditProject = memberPermissions.hasPermissions(Permission.EDIT_PROJECT);

			// If the member can edit the project, check if there are any other project editors left within the project to avoid a situation where nobody is able to edit the project.
			if (memberCanEditProject) {
				permissionCountStatement.setString(1, projectId);
				ResultSet permissionCountResult = permissionCountStatement.executeQuery();
				if (permissionCountResult.getInt(1) < 2) return;
			}

			deleteStatement.setString(1, projectId);
			deleteStatement.setString(2, request.userId());
			deleteStatement.executeUpdate();
		}
	}

	public record Request(String userId) {
		public static final Codec<Request> CODEC = User.ID_CODEC.xmap(Request::new, Request::userId);
	}
}
