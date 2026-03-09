package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

import java.util.Map;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(PATCH)
@EndpointPath("/v2/projects/{project_id}/members")
public class ModifyMembersEndpoint extends AuthorizedProjectEndpoint {
	public ModifyMembersEndpoint() {
		super("{project_id}/members", true);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		String projectId = this.getProjectId(ctx);
		DatabaseAccess db = DatabaseAccess.get();
		Request request = this.decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		for (Map.Entry<String, String> entry : request.users().entrySet()) {
			String memberId = entry.getKey();
			String role = entry.getValue();

			// If a non-administrator attempts to modify an administrator, return.
			if (userCannotModifyMember(ctx, projectId, memberId, scopePermissions)) return;

			if (role == null) {
				Permissions memberPermissions = db.getProjectMemberPermissions(memberId, projectId)
						.unwrap(ctx);

				if (memberPermissions == null) return;

				boolean memberIsAdmin = memberPermissions.hasPermissions(Permission.ADMINISTRATOR);

				// If the member can edit the project, check if there are any other project editors left within the project to avoid a situation where nobody is able to edit the project.
				// check if admin can edit admin and other admin exist, and we are admin this scope
				if (memberIsAdmin && scopePermissions.hasPermissions(Permission.ADMINISTRATOR)) {
					if (db.getProjectAdministratorCount(projectId) < 2) {
						ctx.status(400);
						ctx.result("A project must have at least one administrator");
						return;
					}
				}

				db.removeProjectMember(projectId, memberId);
				continue;
			}

			Permissions permissions = db.getProjectMemberPermissions(memberId, projectId)
					.unwrap(ctx);

			if (permissions == null) {
				db.addProjectMember(projectId, memberId);
			}

			if (!role.isEmpty()) {
				db.setRoleName(projectId, memberId, role);
			} else {
				db.setRoleName(projectId, memberId, "Member");
			}
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		return ctx.pathParam("project_id");
	}

	public record Request(Map<String, String> users) {
		public static final Codec<Request> CODEC = Codec.unboundedMap(User.ID_CODEC, Codec.STRING)
				.xmap(Request::new, Request::users);
	}
}
