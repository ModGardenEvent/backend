package net.modgarden.backend.endpoint.v2.event;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/event/{event_type_slug}/{event_slug}/id/{submission_id}")
public class GetSubmissionByIdEndpoint extends GetSubmissionEndpoint {
	public GetSubmissionByIdEndpoint() {
		super("id/{submission_id}");
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		String eventTypeSlug = ctx.pathParam("event_type_slug").toLowerCase(Locale.ROOT);
		String eventSlug = ctx.pathParam("event_slug").toLowerCase(Locale.ROOT);
		String submissionId = ctx.pathParam("submission_id").toLowerCase(Locale.ROOT);

		try (
				var connection = this.getDatabaseConnection();
				var eventStatement = connection.prepareStatement("""
					SELECT id
					FROM events
					WHERE event_type_slug = ? AND slug = ?
				""");
				var submissionsStatement = connection.prepareStatement("""
					SELECT 1
					FROM submissions
					WHERE id = ? AND event = ?
				""")
		) {
			eventStatement.setString(1, eventTypeSlug);
			eventStatement.setString(2, eventSlug);
			var eventResult = eventStatement.executeQuery();

			if (!eventResult.isBeforeFirst()) {
				ctx.result("Could not find event '" + eventSlug + "' for event type '" + eventTypeSlug + "'.");
				ctx.status(404);
				return;
			}

			String event = eventResult.getString("id");

			submissionsStatement.setString(1, submissionId);
			submissionsStatement.setString(2, event);
			var submissionsResult = submissionsStatement.executeQuery();

			if (!submissionsResult.getBoolean(1)) {
				ctx.result("Could not find submission '" + submissionId + "' for event '" + eventSlug + "' for event type '" + eventTypeSlug + "'.");
				ctx.status(404);
				return;
			}

			Submission submission = GetSubmissionEndpoint.getSubmissionFromId(connection, submissionId);
			ctx.json(submission);
			ctx.status(200);
		}
	}
}
