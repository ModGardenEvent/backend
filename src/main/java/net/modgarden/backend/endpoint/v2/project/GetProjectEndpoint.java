package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.data.Metadata;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.metadata.DraftMetadata;
import net.modgarden.backend.data.event.metadata.ModMetadata;
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

	// TODO: Require view project permissions or being a member of the project to view draft projects.
	// todo: cali why is this not in DatabaseAccess :tiny_pineapple:
	public static Project getProjectFromId(@NotNull Connection connection,
										   @NotNull String projectId) throws Exception {
		Map<String, String> team = new HashMap<>();
		Map<String, Long> permissions = new HashMap<>();
		List<String> submissions = new ArrayList<>();
		try (
				var projectRolesStatement = connection.prepareStatement("""
					SELECT user_id, permissions, role_name
					FROM project_roles
					WHERE project_id = ?
				""");
				var projectDraftMetadataStatement = connection.prepareStatement("""
					SELECT name
					FROM project_draft_metadata
					WHERE project_id = ?
				""");
				var projectModMetadataStatement = connection.prepareStatement("""
					SELECT mod_id, name, description, source_url
					FROM project_mod_metadata
					WHERE project_id = ?
				""");
				var submissionsStatement = connection.prepareStatement("""
					SELECT id
					FROM submissions
					WHERE project_id = ?
				""")
		) {
			projectModMetadataStatement.setString(1, projectId);
			ResultSet projectModMetadataResult = projectModMetadataStatement.executeQuery();

			projectDraftMetadataStatement.setString(1, projectId);
			ResultSet projectDraftMetadataResult = projectDraftMetadataStatement.executeQuery();

			Metadata metadata;
			if (projectModMetadataResult.isBeforeFirst()) {
				metadata = new ModMetadata(
						projectModMetadataResult.getString("mod_id"),
						projectModMetadataResult.getString("name"),
						projectModMetadataResult.getString("description"),
						projectModMetadataResult.getString("source_url")
				);
			} else if (projectDraftMetadataResult.isBeforeFirst()) {
				metadata = new DraftMetadata(
						projectDraftMetadataResult.getString("name")
				);
			} else {
				throw new NullPointerException("Could not find metadata for project '" + projectId + "'");
			}


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
					metadata,
					team,
					permissions,
					submissions
			);
		}
	}
}
