package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(DELETE)
@EndpointPath("/v2/projects/{project_id}")
public class DeleteProjectEndpoint extends AuthorizedProjectEndpoint {
	public DeleteProjectEndpoint() {
		super("{project_id}", false);
	}

	@Override
	public Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		String projectId = this.getProjectId(ctx);
		DatabaseAccess db = DatabaseAccess.get();
		db.deleteProject(projectId);
		return Response.ok();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.EDIT_PROJECT);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		return ctx.pathParam("project_id");
	}
}
