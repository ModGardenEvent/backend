package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedSubmissionEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/submission/{submission_id}/delete")
public class DeleteSubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	public DeleteSubmissionEndpoint() {
		super("{submission_id}/delete", PermissionScope.PROJECT, false);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		Permissions userPermissions = getDatabaseAccess()
				.getUserPermissions(userId)
				.unwrap(ctx);
		if (userPermissions == null || !scopePermissions.hasPermissions(Permission.EDIT_PROJECT) && !userPermissions.hasPermissions(Permission.MODERATE_PROJECTS)) {
			ctx.status(403);
			ctx.result("User lacks permission; required " + Permission.EDIT_PROJECT);
			return;
		}

		String submissionId = ctx.pathParam("submission_id");

		try (
				var connection = this.getDatabaseConnection();
				var statement = connection.prepareStatement("""
					DELETE FROM submissions
					WHERE id = ?
				""")
		) {
			statement.setString(1, submissionId);
			statement.executeUpdate();
		}
	}
}
