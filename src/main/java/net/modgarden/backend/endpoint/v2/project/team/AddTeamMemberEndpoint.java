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

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PUT;

@EndpointMethod(PUT)
@EndpointPath("/v2/project/{project_id}/team/{user_id}")
public class AddTeamMemberEndpoint extends AuthorizedProjectEndpoint {
	public AddTeamMemberEndpoint() {
		super("{project_id}/team/{user_id}", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		String projectId = ctx.pathParam("project_id");
		String memberUserId = ctx.pathParam("user_id");

		try (
				var connection = this.getDatabaseConnection();
				var checkStatement = connection.prepareStatement("""
					SELECT 1
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""");
				var insertStatement = connection.prepareStatement("""
					INSERT INTO project_roles (project_id, user_id)
					VALUES (?, ?)
				""")
		) {
			checkStatement.setString(1, projectId);
			checkStatement.setString(2, memberUserId);
			ResultSet checkResult = checkStatement.executeQuery();

			// Check if the user is already a member of the project to avoid an exception being thrown.
			if (checkResult.getBoolean(1)) return;

			insertStatement.setString(1, projectId);
			insertStatement.setString(2, memberUserId);
			insertStatement.executeUpdate();
		}
	}
}
