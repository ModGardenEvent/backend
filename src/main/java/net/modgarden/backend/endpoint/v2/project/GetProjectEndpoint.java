package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;

@EndpointPath("/v2/project")
public abstract class GetProjectEndpoint extends Endpoint {
	public GetProjectEndpoint(String path) {
		super(2, "project/" + path);
	}

	@Override
	public abstract void handle(@NotNull Context ctx) throws Exception;

	public static Project getProjectFromId(@NotNull Connection connection,
										   @NotNull String projectId) throws Exception {
		Map<String, String> team = new HashMap<>();
		Map<String, Long> permissions = new HashMap<>();
		List<String> submissions = new ArrayList<>();
		try (
				var projectRolesStatement = connection.prepareStatement("SELECT user_id, permissions, role_name FROM project_roles WHERE project_id = ?");
				var projectMetadataStatement = connection.prepareStatement("SELECT mod_id, name, description, source_url, icon_url, banner_url FROM project_metadata WHERE project_id = ?");
				var submissionsStatement = connection.prepareStatement("SELECT id FROM submissions WHERE project_id = ?")
		) {
			projectMetadataStatement.setString(1, projectId);
			ResultSet projectMetadataResult = projectMetadataStatement.executeQuery();

			projectRolesStatement.setString(1, projectId);
			ResultSet projectRolesResult = projectRolesStatement.executeQuery();
			while (projectRolesResult.next()) {
				String projectRoleUserId = projectRolesResult.getString("user_id");
				team.put(projectRoleUserId, projectRolesResult.getString("role_name"));
				permissions.put(projectRoleUserId, projectRolesResult.getLong("permissions"));
			}

			submissionsStatement.setString(1, projectId);
			ResultSet submissionsResult = submissionsStatement.executeQuery();
			while (submissionsResult.next()) {
				submissions.add(submissionsResult.getString("id"));
			}

			return new Project(
					projectId,
					// TODO: Add project types to database.
					Project.Type.MOD,
					new Project.Metadata(
							projectMetadataResult.getString("mod_id"),
							projectMetadataResult.getString("name"),
							projectMetadataResult.getString("description"),
							projectMetadataResult.getString("source_url"),
							projectMetadataResult.getString("icon_url"),
							projectMetadataResult.getString("banner_url")
					),
					team,
					permissions,
					submissions,
					// TODO: Add ext field to the database.
					Collections.emptyMap()
			);
		}
	}
}
