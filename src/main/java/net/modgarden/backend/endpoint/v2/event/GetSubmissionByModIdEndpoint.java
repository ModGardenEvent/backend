package net.modgarden.backend.endpoint.v2.event;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.submission.GetSubmissionEndpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/event/{event_slug}/{theme_slug}/mod_id/{mod_id}")
public class GetSubmissionByModIdEndpoint extends GetSubmissionEndpoint {
	public GetSubmissionByModIdEndpoint() {
		super("event/{event_slug}/{theme_slug}/mod_id/{mod_id}");
	}

	@SuppressWarnings("DuplicatedCode")
	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String eventSlug = ctx.pathParam("event_slug").toLowerCase(Locale.ROOT);
		String themeSlug = ctx.pathParam("theme_slug").toLowerCase(Locale.ROOT);
		String modId = ctx.pathParam("mod_id").toLowerCase(Locale.ROOT);

		String themeId = db.getThemeId(eventSlug, themeSlug);

		if (themeId == null) {
			ctx.result("Could not find theme '" + themeSlug + "' for event '" + eventSlug + "'");
			ctx.status(404);
			return;
		}

		String projectId = db.getProjectIdFromModId(modId);

		if (projectId == null) {
			ctx.result("Could not find mod with ID '" + modId + "'");
			ctx.status(404);
			return;
		}

		String submissionId = db.getSubmissionId(projectId, themeId);

		if (submissionId == null) {
			ctx.result("Could not find submission for mod with ID '" + modId + "' for theme '" + themeSlug + "' of event '" + eventSlug + "'");
			ctx.status(404);
			return;
		}

		Submission submission = db.getSubmission(submissionId).getObject();
		ctx.json(submission);
		ctx.status(200);
	}
}
