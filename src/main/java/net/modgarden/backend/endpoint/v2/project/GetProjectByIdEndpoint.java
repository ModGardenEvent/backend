package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/project/{project_id}")
public class GetProjectByIdEndpoint extends GetProjectEndpoint {
	public GetProjectByIdEndpoint() {
		super("{project_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		String projectId = ctx.pathParam("project_id");
		DatabaseAccess db = DatabaseAccess.get();

		if (!db.projectExists(projectId)) {
			ctx.result("Could not find project from id '" + projectId + "'");
			ctx.status(404);
			return;
		}

		ctx.json(db.getProjectFromId(projectId));
		ctx.status(200);
	}
}
