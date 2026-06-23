package net.modgarden.backend.endpoint.internal.role;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.DELETE;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.user.role.DiscordUserRoleIntegration;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.internal.InternalEndpoint;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(DELETE)
@EndpointPath("/internal/role/{role_id}")
public class DeleteRoleEndpoint extends InternalEndpoint {
	public DeleteRoleEndpoint() {
		super("role/{role_id}");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String roleId = ctx.pathParam("role_id");
		db.deleteUserRole(roleId);
		return Response.ok();
	}
}
