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
@EndpointPath("/v2/project/mod_id/{mod_id}")
public class GetProjectByModIdEndpoint extends GetProjectEndpoint {
	public GetProjectByModIdEndpoint() {
		super("mod_id/{mod_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		String modId = ctx.pathParam("mod_id");
		DatabaseAccess db = DatabaseAccess.get();
		String projectId = db.getProjectIdFromModId(modId);

		if (projectId == null) {
			ctx.result("Could not find project from mod ID '" + modId + "'");
			ctx.status(404);
			return;
		}

		ctx.json(db.getProjectFromId(projectId));
		ctx.status(200);
	}
}
