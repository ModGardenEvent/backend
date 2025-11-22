package net.modgarden.backend.endpoint.v2.event;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.submission.GetSubmissionEndpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/event/{event_type_slug}/{event_slug}/mod_id/{mod_id}")
public class GetSubmissionByModIdEndpoint extends GetSubmissionEndpoint {
	public GetSubmissionByModIdEndpoint() {
		super("event/{event_type_slug}/{event_slug}/mod_id/{mod_id}");
	}

	@SuppressWarnings("DuplicatedCode")
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		String eventTypeSlug = ctx.pathParam("event_type_slug").toLowerCase(Locale.ROOT);
		String eventSlug = ctx.pathParam("event_slug").toLowerCase(Locale.ROOT);
		String modId = ctx.pathParam("mod_id").toLowerCase(Locale.ROOT);

		try (
				var connection = this.getDatabaseConnection();
				var eventStatement = connection.prepareStatement("""
					SELECT id
					FROM events
					WHERE event_type_slug = ? AND slug = ?
				""");
				var projectMetadataStatement = connection.prepareStatement("""
					SELECT project_id
					FROM project_metadata
					WHERE mod_id = ?
				""");
				var submissionsStatement = connection.prepareStatement("""
					SELECT id
					FROM submissions
					WHERE project_id = ? AND event = ?
				""")
		) {
			projectMetadataStatement.setString(1, modId);
			var projectMetadataResult = projectMetadataStatement.executeQuery();

			if (!projectMetadataResult.isBeforeFirst()) {
				ctx.result("Could not find mod with id '" + modId + "'");
				ctx.status(404);
				return;
			}

			eventStatement.setString(1, eventTypeSlug);
			eventStatement.setString(2, eventSlug);
			var eventResult = eventStatement.executeQuery();

			if (!eventResult.isBeforeFirst()) {
				ctx.result("Could not find event '" + eventSlug + "' for event type '" + eventTypeSlug + "'");
				ctx.status(404);
				return;
			}

			String projectId = projectMetadataResult.getString("project_id");
			String event = eventResult.getString("id");

			submissionsStatement.setString(1, projectId);
			submissionsStatement.setString(2, event);
			var submissionsResult = submissionsStatement.executeQuery();

			String submissionId = submissionsResult.getString("id");

			if (submissionId == null) {
				ctx.result("Could not find submission for mod with ID '" + modId + "' for event '" + eventSlug + "' for event type '" + eventTypeSlug + "'");
				ctx.status(404);
				return;
			}

			Submission submission = GetSubmissionEndpoint.getSubmissionFromId(connection, submissionId);
			ctx.json(submission);
			ctx.status(200);
		}
	}
}
