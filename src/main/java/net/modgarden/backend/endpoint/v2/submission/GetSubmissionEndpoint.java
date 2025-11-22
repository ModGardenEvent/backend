package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.event.platform.ModrinthPlatform;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.v2.project.GetProjectEndpoint;
import net.modgarden.backend.util.ModrinthUtils;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;

public abstract class GetSubmissionEndpoint extends Endpoint {
	public GetSubmissionEndpoint(String path) {
		super(2, path);
	}

	@Override
	public abstract void handle(@NotNull Context ctx) throws Exception;

	public static Submission getSubmissionFromId(@NotNull Connection connection,
												 @NotNull String submissionId) throws Exception {
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
					GetProjectEndpoint.getProjectFromId(connection, submissionResult.getString("project_id")),
					platform
			);
		}
	}
}
