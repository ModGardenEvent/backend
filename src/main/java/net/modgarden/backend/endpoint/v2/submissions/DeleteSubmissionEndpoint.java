package net.modgarden.backend.endpoint.v2.submissions;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

import java.sql.SQLException;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.exception.HypertextException;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(DELETE)
@EndpointPath("/v2/submissions/{submission_id}")
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
		DatabaseAccess db = DatabaseAccess.get();
		db.deleteSubmission(submissionId);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) throws SQLException, HypertextException {
		return DatabaseAccess.get().getProjectIdFromSubmissionId(ctx.pathParam("submission_id"));
	}
}
