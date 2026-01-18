package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.database.DatabaseAccess;
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
	public void onRequest(@NotNull Context ctx) throws Exception {
		String submissionId = ctx.pathParam("submission_id").toLowerCase(Locale.ROOT);
		DatabaseAccess db = DatabaseAccess.get();
		Submission submission = db.getSubmission(submissionId)
				.unwrap(ctx);

		if (submission == null) return;

		ctx.json(submission);
		ctx.status(200);
	}
}
