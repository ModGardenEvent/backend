package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

import java.util.Map;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.BadRequestException;
import net.modgarden.backend.endpoint.exception.ForbiddenException;
import net.modgarden.backend.util.codec.NullableCodec;
import net.modgarden.backend.util.NullableWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(PATCH)
@EndpointPath("/v2/projects/{project_id}/members")
public class ModifyMembersEndpoint extends AuthorizedProjectEndpoint {
	public ModifyMembersEndpoint() {
		super("{project_id}/members", true);
	}

	@Override
	public Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		String projectId = this.getProjectId(ctx);
		Request request = this.decodeBody(ctx, Request.CODEC);

		for (Map.Entry<String, NullableWrapper<String>> entry : request.users().entrySet()) {
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

		return Response.ok();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.EDIT_PROJECT);
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		return ctx.pathParam("project_id");
	}

	public record Request(Map<String, NullableWrapper<String>> users) {
		public static final Codec<Request> CODEC = Codec.unboundedMap(User.ID_CODEC, NullableCodec.nullable(Codec.STRING))
				.xmap(Request::new, Request::users);
	}
}
