package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;

@EndpointPath("/v2/project")
public abstract class AuthorizedProjectEndpoint extends AuthorizedEndpoint {
	public AuthorizedProjectEndpoint(String path, boolean hasBody) {
		this(path, PermissionScope.PROJECT, hasBody);
	}

	protected AuthorizedProjectEndpoint(String path, PermissionScope scope, boolean hasBody) {
		super(2, "project/" + path, scope, hasBody);
	}

	@Override
	public abstract void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	@NotNull
	@Override
	protected abstract String getProjectId(Context ctx);

	protected static boolean requireUserCanModifyMember(
			Context ctx,
			Connection connection,
			String projectId,
			String memberUserIdToModify,
			Permissions selfPermissions
	) throws Exception {
		try (
				var memberPermissionsStatement = connection.prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")
		) {
			memberPermissionsStatement.setString(1, projectId);
			memberPermissionsStatement.setString(2, memberUserIdToModify);
			ResultSet memberPermissionsResult = memberPermissionsStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// If a non-administrator attempts to edit the permissions of an administrator, return false.
			if (memberPermissions.hasPermissions(Permission.ADMINISTRATOR) && !selfPermissions.hasPermissions(Permission.ADMINISTRATOR)) {
				ctx.status(403);
				ctx.result("Non-administrators may not edit administrators' permissions on projects");
				return true;
			}
		}

		return false;
	}
}
