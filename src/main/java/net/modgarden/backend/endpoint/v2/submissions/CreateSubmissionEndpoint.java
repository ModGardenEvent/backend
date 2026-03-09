package net.modgarden.backend.endpoint.v2.submissions;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(POST)
@EndpointPath("/v2/submissions")
public class CreateSubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		DatabaseAccess db = DatabaseAccess.get();
		String submissionId = db.createEmptySubmission(request.eventId(), request.projectId());
		request.platform().addToDatabase(db, request.projectId(), submissionId);
		ctx.status(201);
		ctx.header("Location", "/v2/submissions/" + submissionId);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		HypertextResult<Request> result = decodeBody(ctx, Request.CODEC);
		Request request = result.unwrap(ctx);

		if (request == null) {
			throw new IllegalStateException(result.getMessage());
		}

		return request.projectId();
	}

	public record Request(String projectId, String eventId, Platform platform) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Project.ID_CODEC.fieldOf("project").forGetter(Request::projectId),
				Event.ID_CODEC.fieldOf("event_id").forGetter(Request::eventId),
				Submission.PLATFORM_CODEC.fieldOf("platform").forGetter(Request::platform)
		).apply(inst, Request::new));
	}
}
