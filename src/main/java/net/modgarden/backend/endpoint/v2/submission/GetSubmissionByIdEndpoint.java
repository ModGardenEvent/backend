package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/submission/{submission_id}")
public class GetSubmissionByIdEndpoint extends GetSubmissionEndpoint {
	public GetSubmissionByIdEndpoint() {
		super("submission/{submission_id}");
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		String submissionId = ctx.pathParam("submission_id").toLowerCase(Locale.ROOT);

		try (
				var connection = this.getDatabaseConnection();
				var submissionsStatement = connection.prepareStatement("""
					SELECT 1
					FROM submissions
					WHERE id = ?
				""")
		) {
			submissionsStatement.setString(1, submissionId);
			var submissionsResult = submissionsStatement.executeQuery();

			if (!submissionsResult.getBoolean(1)) {
				ctx.result("Could not find submission '" + submissionId + "'");
				ctx.status(404);
				return;
			}

			Submission submission = GetSubmissionEndpoint.getSubmissionFromId(connection, submissionId);
			ctx.json(submission);
			ctx.status(200);
		}
	}
}
