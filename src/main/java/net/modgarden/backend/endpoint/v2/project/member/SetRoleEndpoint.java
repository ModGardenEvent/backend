package net.modgarden.backend.endpoint.v2.project.member;

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

import java.util.Map;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PUT;

@EndpointMethod(PUT)
@EndpointPath("/v2/project/{project_id}/set_role")
public class SetRoleEndpoint extends AuthorizedProjectEndpoint {
	public SetRoleEndpoint() {
		super("{project_id}/set_role", true);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		String projectId = this.getProjectId(ctx);

		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		DatabaseAccess db = DatabaseAccess.get();

		for (Map.Entry<String, String> usersToRoleName : request.usersToRoleName().entrySet()) {
			if (userCannotModifyMember(ctx, projectId, usersToRoleName.getKey(), scopePermissions)) return;

			db.setRoleName(projectId, userId, usersToRoleName.getValue());
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		return ctx.pathParam("project_id");
	}

	public record Request(Map<String, String> usersToRoleName) {
		public static final Codec<Request> CODEC = Codec.unboundedMap(User.ID_CODEC, Codec.STRING)
				.xmap(Request::new, Request::usersToRoleName);
	}
}
