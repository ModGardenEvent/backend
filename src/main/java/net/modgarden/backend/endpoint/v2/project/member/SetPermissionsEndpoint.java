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
@EndpointPath("/v2/project/{project_id}/set_permissions")
public class SetPermissionsEndpoint extends AuthorizedProjectEndpoint {
	public SetPermissionsEndpoint() {
		super("{project_id}/set_permissions", true);
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

		for (Map.Entry<String, Permissions> usersToPermissions : request.usersToPermissions().entrySet()) {
			if (userCannotModifyMember(
					ctx,
					projectId,
					usersToPermissions.getKey(),
					scopePermissions
			)) return;

			db.setProjectMemberPermissions(usersToPermissions.getValue(), projectId, usersToPermissions.getKey());
		}
	}

	@NotNull
	@Override
	protected String getProjectId(Context ctx) {
		return ctx.pathParam("project_id");
	}

	public record Request(Map<String, Permissions> usersToPermissions) {
		public static final Codec<Request> CODEC = Codec.unboundedMap(User.ID_CODEC, Permission.STRING_PERMISSIONS_CODEC)
				.xmap(Request::new, Request::usersToPermissions);
	}
}
