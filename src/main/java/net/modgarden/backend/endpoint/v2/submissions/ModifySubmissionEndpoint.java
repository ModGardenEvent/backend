package net.modgarden.backend.endpoint.v2.submissions;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

import java.sql.SQLException;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.project.Project;
import net.modgarden.backend.data.project.Submission;
import net.modgarden.backend.data.project.SubmissionPlatform;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.ForbiddenException;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.InternalServerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(PATCH)
@EndpointPath("/v2/submissions/{submission_id}")
public class ModifySubmissionEndpoint extends AuthorizedSubmissionEndpoint {
	public ModifySubmissionEndpoint() {
		super("{submission_id}", true);
	}

	@Override
	public Response onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String submissionId = ctx.pathParam("submission_id");
		String projectId = this.getProjectId(ctx);
		Project project = db.getProjectFromId(projectId);
		Request request = this.decodeBody(ctx, Request.CODEC);

		if (request.projectId() != null) {
			projectId = request.projectId();
			boolean isMember = project.permissions().containsKey(userId);

			if (!isMember) {
				throw new ForbiddenException("User is not a member of the project with ID '" + projectId + "'.");
			}

			boolean hasPermissions = project.permissions().get(userId).hasPermissions(Permission.ADMINISTRATOR);

			if (!hasPermissions) {
				throw new ForbiddenException();
			}

			throw new InternalServerException("Submission transferring is not implemented yet.");
		}

		if (request.platform() != null) {
			db.deleteSubmissionData(submissionId);
			request.platform().addToDatabase(db, projectId, submissionId);
		}

		return Response.ok();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.EDIT_PROJECT);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) throws SQLException, HypertextException {
		return DatabaseAccess.get().getProjectIdFromSubmissionId(ctx.pathParam("submission_id"));
	}

	public record Request(
			@Nullable String projectId,
			@Nullable SubmissionPlatform platform
	) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Project.ID_CODEC
						.optionalFieldOf("project_id")
						.forGetter(request -> Optional.ofNullable(request.projectId())),
				Submission.PLATFORM_CODEC
						.optionalFieldOf("platform")
						.forGetter(request -> Optional.ofNullable(request.platform()))
		).apply(instance, (projectId, platform) -> new Request(projectId.orElse(null), platform.orElse(null))));
	}
}
