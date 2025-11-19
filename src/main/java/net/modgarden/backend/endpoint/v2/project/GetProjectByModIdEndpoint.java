package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Project;
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
	public void handle(@NotNull Context ctx) throws Exception {
		String modId = ctx.pathParam("mod_id");

		try (
				var connection = this.getDatabaseConnection();
				var projectStatement = connection.prepareStatement("SELECT project_id FROM project_metadata WHERE mod_id = ?")
		) {
			projectStatement.setString(1, modId);
			ResultSet projectResult = projectStatement.executeQuery();
			if (!projectResult.isBeforeFirst()) {
				ctx.result("Could not find project from mod id '" + modId + "'.");
				ctx.status(404);
				return;
			}
			String projectId = projectResult.getString("project_id");
			Project project = getProjectFromId(connection, projectId);

			if (project == null) {
				ctx.result("Could not create project object from mod id '" + modId + "'.");
				ctx.status(500);
				return;
			}

			ctx.json(project);
			ctx.status(200);
		}
	}
}
