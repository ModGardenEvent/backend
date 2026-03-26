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
import org.jetbrains.annotations.NotNull;

@EndpointMethod(PATCH)
@EndpointPath("/v2/projects/{project_id}/permissions")
public class SetPermissionsEndpoint extends AuthorizedProjectEndpoint {
	public SetPermissionsEndpoint() {
		super("{project_id}/permissions", true);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT)) return;

		String projectId = this.getProjectId(ctx);

		Request request = decodeBody(ctx, Request.CODEC);

		DatabaseAccess db = DatabaseAccess.get();

		// This is a separate loop to make sure that no actions are made if there is one error.
		for (Map.Entry<String, Permissions> usersToPermissions : request.usersToPermissions().entrySet()) {
			db.assertUserCanModifyMember(projectId, usersToPermissions.getKey(), scopePermissions);
		}

		for (Map.Entry<String, Permissions> usersToPermissions : request.usersToPermissions().entrySet()) {
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
