package net.modgarden.backend.endpoint.v2.submissions;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import java.util.Locale;

import io.javalin.http.Context;
import net.modgarden.backend.data.project.Submission;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/submissions/{submission_id}")
public class GetSubmissionEndpoint extends Endpoint {
	public GetSubmissionEndpoint() {
		super(2, "submissions/{submission_id}");
	}

	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		String submissionId = ctx.pathParam("submission_id").toLowerCase(Locale.ROOT);
		DatabaseAccess db = DatabaseAccess.get();
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SUBMISSION_ID);

		switch (queryKey) {
		case SUBMISSION_ID -> {
		}
		case MOD_ID -> submissionId = db.getLatestSubmissionIdFromModId(submissionId);
		default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
		}

		return Response.ok(db.getSubmission(submissionId));
	}
}
