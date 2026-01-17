package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedSubmissionEndpoint;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

@EndpointMethod(DELETE)
@EndpointPath("/v2/submission/{submission_id}")
public class DeleteSubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	public DeleteSubmissionEndpoint() {
		super("{submission_id}", false);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		String submissionId = ctx.pathParam("submission_id");

		try (
				var connection = this.getDatabaseConnection();
				var submissionsStatement = connection.prepareStatement("""
					DELETE FROM submissions
					WHERE id = ?
				""");
				var typeModrinthStatement = connection.prepareStatement("""
					DELETE FROM submission_type_modrinth
					WHERE submission_id = ?
				""")
		) {
			submissionsStatement.setString(1, submissionId);
			submissionsStatement.executeUpdate();

			typeModrinthStatement.setString(1, submissionId);
			typeModrinthStatement.executeUpdate();
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) throws SQLException {
		return this.getDatabaseAccess().getProjectIdFromSubmissionId(ctx.pathParam("submission_id"));
	}
}
