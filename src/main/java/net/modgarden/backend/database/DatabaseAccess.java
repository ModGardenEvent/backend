package net.modgarden.backend.database;

import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Metadata;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.event.metadata.DraftMetadata;
import net.modgarden.backend.data.event.metadata.ModMetadata;
import net.modgarden.backend.data.event.platform.ModrinthPlatform;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseAccess {
	public Connection getDatabaseConnection() throws SQLException {
		return ModGardenBackend.createDatabaseConnection();
	}

	public String getProjectIdFromSubmissionId(
			@NotNull String submissionId
	) throws SQLException, NullPointerException {
		Connection connection = this.getDatabaseConnection();
		try (
				var submissionIdStatement = connection.prepareStatement("""
					SELECT project_id
					FROM submissions
					WHERE id = ?
				""");
		) {
			submissionIdStatement.setString(1, submissionId);
			ResultSet submissionResult = submissionIdStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NullPointerException("Could not find submission '" + submissionId + "'");
			}

			return submissionResult.getString("project_id");
		}
	}

	public Submission getSubmissionFromId(
			@NotNull String submissionId
	) throws Exception {
		Connection connection = this.getDatabaseConnection();
		try (
				var submissionStatement = connection.prepareStatement("""
					SELECT event, project_id, submitted
					FROM submissions
					WHERE id = ?
				""");
				var modrinthSubmissionTypeStatement = connection.prepareStatement("""
					SELECT modrinth_id, version_id
					FROM submission_type_modrinth
					WHERE submission_id = ?
				""")
		) {
			submissionStatement.setString(1, submissionId);
			ResultSet submissionResult = submissionStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NullPointerException("Could not find submission '" + submissionId + "'");
			}

			modrinthSubmissionTypeStatement.setString(1, submissionId);
			ResultSet modrinthSubmissionTypeResult = modrinthSubmissionTypeStatement.executeQuery();

			Platform platform;
			// TODO: Implement download URL submission type.
			if (modrinthSubmissionTypeResult.isBeforeFirst()) {
				platform = new ModrinthPlatform(
						modrinthSubmissionTypeResult.getString("modrinth_id"),
						modrinthSubmissionTypeResult.getString("version_id")
				);
			} else {
				throw new RuntimeException("Submission does not have a valid 'platform'");
			}

			return new Submission(
					submissionId,
					submissionResult.getString("event"),
					submissionResult.getLong("submitted"),
					this.getProjectFromId(submissionResult.getString("project_id")),
					platform
			);
		}
	}

	public Project getProjectFromId(
			@NotNull String projectId
	) throws Exception {
		Connection connection = this.getDatabaseConnection();
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

	public HypertextResult<Permissions> getUserPermissions(String userId) throws SQLException {
		try (
				var connection = getDatabaseConnection();
				var userStatement = connection.prepareStatement("SELECT permissions FROM users WHERE id = ?")
		) {
			userStatement.setString(1, userId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				return new HypertextResult<>(404, "User does not exist.");
			}

			return new HypertextResult<>(new Permissions(resultSet.getLong("permissions")));
		}
	}

	public HypertextResult<Permissions> getProjectPermissions(String userId, String projectId) throws SQLException {
		try (
				var connection = getDatabaseConnection();
				var userStatement = connection.prepareStatement("SELECT permissions FROM project_roles WHERE user_id = ? AND project_id = ?")
		) {
			userStatement.setString(1, userId);
			userStatement.setString(2, projectId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				return new HypertextResult<>(404, "User does not have a role in the specified project.");
			}

			return new HypertextResult<>(new Permissions(resultSet.getLong("permissions")));
		}
	}
}
