package net.modgarden.backend.endpoint.v2.project.team;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/project/{project_id}/team/{user_id}")
public class RemoveTeamMemberEndpoint extends AuthorizedProjectEndpoint {
	public RemoveTeamMemberEndpoint() {
		super("{project_id}/team/{user_id}", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (!scopePermissions.hasPermissions(Permission.EDIT_PROJECT) && !scopePermissions.hasPermissions(Permission.MODERATE_PROJECTS)) {
			ctx.status(403);
			ctx.result("User lacks permission; required " + Permission.EDIT_PROJECT);
			return;
		}

		String projectId = ctx.pathParam("project_id");
		String memberUserId = ctx.pathParam("user_id");

		try (
				var connection = this.getDatabaseConnection();
				var permissionCheckStatement = connection.prepareStatement("""
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
			permissionCheckStatement.setString(1, projectId);
			permissionCheckStatement.setString(2, memberUserId);
			ResultSet memberPermissionsResult = permissionCheckStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// TODO: Figure out if team members that can edit project can remove project administrators... That feels unintended.
			boolean memberCanEditProject = memberPermissions.hasPermissions(Permission.EDIT_PROJECT);

			// If the member can edit the project, check if there are any other project editors left within the project to avoid a situation where nobody is able to edit the project.
			if (memberCanEditProject) {
				permissionCountStatement.setString(1, projectId);
				ResultSet permissionCountResult = permissionCountStatement.executeQuery();
				if (permissionCountResult.getInt(1) < 2) return;
			}

			deleteStatement.setString(1, projectId);
			deleteStatement.setString(2, memberUserId);
			deleteStatement.executeUpdate();
		}
	}
}
