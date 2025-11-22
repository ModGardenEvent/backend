package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/project/{project_id}")
public class DeleteProjectEndpoint extends AuthorizedProjectEndpoint {
	public DeleteProjectEndpoint() {
		super("{project_id}", false);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		String projectId = ctx.pathParam("project_id");

		try (
				var connection = this.getDatabaseConnection();
				var statement = connection.prepareStatement("""
					DELETE FROM projects
					WHERE id = ?
				""")
		) {
			statement.setString(1, projectId);
			statement.executeUpdate();
		}
	}
}
