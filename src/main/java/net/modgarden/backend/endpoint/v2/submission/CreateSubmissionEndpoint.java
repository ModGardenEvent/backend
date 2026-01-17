package net.modgarden.backend.endpoint.v2.submission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedSubmissionEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/v2/submission/create")
public class CreateSubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	public CreateSubmissionEndpoint() {
		super("create", true);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		String generatedSubmissionsId = NaturalId.generate("submissions", "id", null, 5);
		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		try (
				var connection = this.getDatabaseConnection();
				var submissionsStatement = connection.prepareStatement("""
					INSERT INTO submissions (id, event, project_id, submitted)
					VALUES (?, ?, ?, ?)
				""")
		) {
			submissionsStatement.setString(1, generatedSubmissionsId);
			submissionsStatement.setString(2, request.event());
			submissionsStatement.setString(3, request.project());
			submissionsStatement.setLong(4, System.currentTimeMillis());
			submissionsStatement.executeUpdate();

			request.platform().addToDatabase(connection, request.project(), generatedSubmissionsId);

			ctx.status(201);
			ctx.header("Location", "/v2/submission/" + generatedSubmissionsId);
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		HypertextResult<Request> result = decodeBody(ctx, Request.CODEC);
		Request request = result.unwrap(ctx);

		if (request == null)
			throw new IllegalStateException(result.getMessage());

		return request.project();
	}

	public record Request(String project, String event, Platform platform) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Project.ID_CODEC.fieldOf("project").forGetter(Request::project),
				Event.ID_CODEC.fieldOf("event").forGetter(Request::event),
				Submission.PLATFORM_CODEC.fieldOf("platform").forGetter(Request::platform)
		).apply(inst, Request::new));
	}
}
