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
	public AuthorizedProjectEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(2, "project/" + path, permissionScope, hasBody);
	}

	@Override
	public abstract void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	protected static boolean canModifyUser(Connection connection,
										   String projectId,
										   String userIdToModify,
										   Permissions selfPermissions) throws Exception {
		try (
				var memberPermissionsStatement = connection.prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")
		) {
			memberPermissionsStatement.setString(1, projectId);
			memberPermissionsStatement.setString(2, userIdToModify);
			ResultSet memberPermissionsResult = memberPermissionsStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// If a non-administrator attempts to edit the permissions of an administrator, return false.
			if (memberPermissions.hasPermissions(Permission.ADMINISTRATOR) && !selfPermissions.hasPermissions(Permission.ADMINISTRATOR))
				return false;
		}

		return true;
	}
}
