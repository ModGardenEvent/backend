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
				var projectMetadataStatement = connection.prepareStatement("""
					SELECT project_id
					FROM project_mod_metadata
					WHERE mod_id = ?
				""")
		) {
			projectMetadataStatement.setString(1, modId);
			ResultSet projectResult = projectMetadataStatement.executeQuery();
			String projectId = projectResult.getString("project_id");

			if (projectId == null) {
				ctx.result("Could not find project from mod id '" + modId + "'");
				ctx.status(404);
				return;
			}

			Project project = GetProjectEndpoint.getProjectFromId(connection, projectId);

			ctx.json(project);
			ctx.status(200);
		}
	}
}
