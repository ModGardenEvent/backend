package net.modgarden.backend.endpoint.v2.project.member;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PUT;

@EndpointMethod(PUT)
@EndpointPath("/v2/project/{project_id}/add_member")
public class AddMemberEndpoint extends AuthorizedProjectEndpoint {
	public AddMemberEndpoint() {
		super("{project_id}/add_member", true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		String projectId = ctx.pathParam("project_id");
		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		try (
				var connection = this.getDatabaseConnection();
				var insertStatement = connection.prepareStatement("""
					INSERT OR IGNORE INTO project_roles (project_id, user_id)
					VALUES (?, ?)
				""")
		) {
			insertStatement.setString(1, projectId);
			insertStatement.setString(2, request.userId());
			insertStatement.executeUpdate();
		}
	}

	public record Request(String userId) {
		public static final Codec<Request> CODEC = User.ID_CODEC.xmap(Request::new, Request::userId);
	}
}
