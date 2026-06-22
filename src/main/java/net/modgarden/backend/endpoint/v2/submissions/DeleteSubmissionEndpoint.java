package net.modgarden.backend.endpoint.v2.submissions;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

import java.sql.SQLException;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.HypertextException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(DELETE)
@EndpointPath("/v2/submissions/{submission_id}")
public class DeleteSubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	public DeleteSubmissionEndpoint() {
		super("{submission_id}", false);
	}

	@Override
	public Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		String submissionId = ctx.pathParam("submission_id");
		DatabaseAccess db = DatabaseAccess.get();
		db.deleteSubmission(submissionId);
		return Response.ok();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.EDIT_PROJECT);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) throws SQLException, HypertextException {
		return DatabaseAccess.get().getProjectIdFromSubmissionId(ctx.pathParam("submission_id"));
	}
}
