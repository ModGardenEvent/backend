package net.modgarden.backend.endpoint.v2.project.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PUT;

@EndpointMethod(PUT)
@EndpointPath("/v2/project/{project_id}/team/{user_id}/set_role")
public class SetTeamMemberRoleEndpoint extends AuthorizedProjectEndpoint {
	public SetTeamMemberRoleEndpoint() {
		super("{project_id}/team/{user_id}/set_role", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		//noinspection DuplicatedCode
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.EDIT_PROJECT, Permission.MODERATE_PROJECTS)) return;

		String projectId = ctx.pathParam("project_id");
		String memberUserId = ctx.pathParam("user_id");

		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		try (
				var connection = this.getDatabaseConnection();
				var statement = connection.prepareStatement("""
					UPDATE project_roles
					SET role_name = ?
					WHERE project_id = ? AND user_id = ?
				""")
		) {
			statement.setString(1, request.roleName());
			statement.setString(2, projectId);
			statement.setString(3, memberUserId);
			statement.executeUpdate();
		}
	}

	public record Request(String roleName) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("role_name").forGetter(Request::roleName)
		).apply(inst, Request::new));
	}
}
