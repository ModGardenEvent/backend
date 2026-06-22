package net.modgarden.backend.endpoint.internal.role;

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

import java.util.Map;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/internal/role/create")
public class CreateRoleEndpoint extends InternalEndpoint {
	public CreateRoleEndpoint() {
		super("role/create");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String newRoleId = db.createUserRole(
				request.name(),
				request.permissions()
		);

		Integration discordIntegration = request.integrations().get(DiscordUserRoleIntegration.ID);
		if (discordIntegration instanceof DiscordUserRoleIntegration(String discordRoleId)) {
			db.setUserRoleDiscordIntegration(newRoleId, discordRoleId);
		}

		return Response.created("/v2/roles/" + newRoleId);
	}

	// TODO: Move 'integrations' into ModifyRoleEndpoint, use modifiable codec in UserRole class.
	public record Request(String name,
						  Permissions permissions,
						  Map<String, Integration> integrations) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING
						.fieldOf("name")
						.forGetter(Request::name),
				Permission.STRING_PERMISSIONS_CODEC
						.fieldOf("permissions")
						.forGetter(Request::permissions),
				UserRole.INTEGRATION_CODEC
						.fieldOf("integrations")
						.forGetter(Request::integrations)
		).apply(inst, Request::new));
	}
}
