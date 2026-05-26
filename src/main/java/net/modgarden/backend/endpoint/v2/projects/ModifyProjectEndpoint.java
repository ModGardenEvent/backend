package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.project.Project;
import net.modgarden.backend.data.project.ProjectMetadata;
import net.modgarden.backend.data.project.metadata.NoneProjectMetadata;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.BadRequestException;
import net.modgarden.backend.endpoint.exception.ForbiddenException;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.codec.NullableCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(PATCH)
@EndpointPath("/v2/projects/{project_id}")
public class ModifyProjectEndpoint extends AuthorizedProjectEndpoint {
	public ModifyProjectEndpoint() {
		super("{project_id}", true);
	}

	@Override
	public Response onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String projectId = this.getProjectId(ctx);
		Project project = db.getProjectFromId(projectId);
		updateMetadata(ctx, project, db, projectId);
		updateTeam(ctx, db, projectId, scopePermissions);
		updatePermissions(ctx, db, projectId, scopePermissions);

		return Response.ok();
	}

	private void updatePermissions(
			@NotNull Context ctx,
			DatabaseAccess db,
			String projectId,
			Permissions scopePermissions
	) throws HypertextException, SQLException {
		RequestPermissions request = this.decodeBody(ctx, RequestPermissions.CODEC);

		// This is a separate loop to make sure that no actions are made if there is one error.
		for (String targetId : request.permissions().keySet()) {
			if (!db.canUserModifyMember(projectId, targetId, scopePermissions)) {
				throw new ForbiddenException("Non-administrators may not edit administrators' permissions on projects");
			}
		}

		for (Map.Entry<String, Permissions> usersToPermissions : request.permissions().entrySet()) {
			db.setProjectMemberPermissions(usersToPermissions.getValue(), projectId, usersToPermissions.getKey());
		}
	}

	private void updateTeam(
			@NotNull Context ctx,
			DatabaseAccess db,
			String projectId,
			Permissions scopePermissions
	) throws HypertextException, SQLException {
		RequestTeam request = this.decodeBody(ctx, RequestTeam.CODEC.codec());

		for (Map.Entry<String, NullableWrapper<String>> entry : request.team().entrySet()) {
			String memberId = entry.getKey();
			String role = entry.getValue().value();

			// If a non-administrator attempts to modify an administrator, throw.
			if (!db.canUserModifyMember(projectId, memberId, scopePermissions)) {
				throw new ForbiddenException("Non-administrators may not edit administrators' permissions on projects");
			}

			if (role == null) {
				Permissions memberPermissions = db.getProjectMemberPermissions(memberId, projectId);

				boolean memberIsAdmin = memberPermissions.hasPermissions(Permission.ADMINISTRATOR);

				// If the member can edit the project, check if there are any other project editors left within the project to avoid a situation where nobody is able to edit the project.
				// check if admin can edit admin and other admin exist, and we are admin this scope
				if (memberIsAdmin && scopePermissions.hasPermissions(Permission.ADMINISTRATOR)) {
					if (db.getProjectAdministratorCount(projectId) < 2) {
						throw new BadRequestException("A project must have at least one administrator");
					}
				}

				db.removeProjectMember(projectId, memberId);
				continue;
			}

			if (!db.hasProjectMemberPermissions(memberId, projectId)) {
				db.addProjectMember(projectId, memberId);
			}

			if (!role.isEmpty()) {
				db.setRoleName(projectId, memberId, role);
			} else {
				db.setRoleName(projectId, memberId, "Member");
			}
		}
	}

	private void updateMetadata(
			@NotNull Context ctx,
			Project project,
			DatabaseAccess db,
			String projectId
	) throws HypertextException, SQLException {
		String typeName;

		ProjectMetadata.Modifiable metadata1 = this.decodeBody(ctx, RequestMetadata.CODEC.codec()).metadata();

		if (metadata1 != null && metadata1.typeName() != null) {
			typeName = metadata1.typeName();
		} else {
			typeName = project.metadata().typeName();
		}

		NoneProjectMetadata.Modifiable noneMetadata = this.decodeBody(ctx, RequestNoneMetadata.CODEC.codec()).metadata();

		boolean typeChanged = !typeName.equals(project.metadata().typeName());

		if (noneMetadata != null && !typeChanged) {
			db.setProjectNoneMetadata(projectId, noneMetadata);
		}

		if (typeChanged) {
			switch (typeName) {
			case "none" -> {
				if (noneMetadata == null) {
					throw new BadRequestException("Project type changed to 'none', but no metadata was found.");
				}

				db.setProjectNoneMetadata(projectId, noneMetadata);
			}
			case "mod" -> throw new BadRequestException("The project type cannot be changed to 'mod' manually. Upload a new mod submission to convert this project to a mod project.");
			}
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) throws SQLException, HypertextException {
		return ctx.pathParam("project_id");
	}

	public record Request(
			Map<String, NullableWrapper<String>> team,
			Map<String, NullableWrapper<Permissions>> permissions
	) {
		public static final MapCodec<Request> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.unboundedMap(Codec.STRING, NullableCodec.nullable(Codec.STRING))
						.optionalFieldOf("team", Collections.emptyMap())
						.forGetter(Request::team),
				Codec.unboundedMap(Codec.STRING, NullableCodec.nullable(Permission.STRING_PERMISSIONS_CODEC))
						.optionalFieldOf("permissions", Collections.emptyMap())
						.forGetter(Request::permissions)
		).apply(inst, Request::new));
	}

	// TODO: dispatch these or something
	public record RequestNoneMetadata(
			NoneProjectMetadata.Modifiable metadata
	) {
		public static final MapCodec<RequestNoneMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				NoneProjectMetadata.Modifiable.CODEC
						.codec()
						.fieldOf("metadata")
						.forGetter(RequestNoneMetadata::metadata)
		).apply(inst, RequestNoneMetadata::new));
	}

	public record RequestMetadata(
			@Nullable ProjectMetadata.Modifiable metadata
	) {
		public static final MapCodec<RequestMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				ProjectMetadata.Modifiable.CODEC
						.codec()
						.optionalFieldOf("metadata")
						.forGetter(request -> Optional.ofNullable(request.metadata()))
		).apply(inst, (metadata) -> new RequestMetadata(metadata.orElse(null))));
	}

	public record RequestTeam(
			Map<String, NullableWrapper<String>> team
	) {
		public static final MapCodec<RequestTeam> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.unboundedMap(User.ID_CODEC, NullableCodec.nullable(Codec.STRING))
						.optionalFieldOf("team", Collections.emptyMap())
						.forGetter(RequestTeam::team)
		).apply(inst, RequestTeam::new));
	}

	public record RequestPermissions(Map<String, Permissions> permissions) {
		public static final Codec<RequestPermissions> CODEC = Codec.unboundedMap(User.ID_CODEC, Permission.STRING_PERMISSIONS_CODEC)
				.xmap(RequestPermissions::new, RequestPermissions::permissions);
	}
}
