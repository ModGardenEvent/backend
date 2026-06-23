package net.modgarden.backend.endpoint.internal.role;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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
import net.modgarden.backend.util.NullableWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(PATCH)
@EndpointPath("/internal/role/{role_id}")
public class ModifyRoleEndpoint extends InternalEndpoint {
	public ModifyRoleEndpoint() {
		super("role/{role_id}");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String roleId = ctx.pathParam("role_id");

		NullableWrapper<Integration> discordIntegration = request.integrations().get(DiscordUserRoleIntegration.ID);

		if (discordIntegration != null && discordIntegration.value() instanceof DiscordUserRoleIntegration(String discordRoleId)) {
			db.setUserRoleDiscordIntegration(roleId, discordRoleId);
		} else if (discordIntegration != null && discordIntegration.value() == null) {
			db.removeUserRoleDiscordIntegration(roleId);
		}

		if (request.name() != null) {
			db.setUserRoleName(roleId, request.name());
		}

		if (request.permissions() != null) {
			db.setUserRolePermissions(roleId, request.permissions());
		}

		return Response.ok();
	}

	public record Request(@Nullable String name,
						  @Nullable Permissions permissions,
						  Map<String, NullableWrapper<Integration>> integrations) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING
						.optionalFieldOf("name")
						.forGetter(o -> Optional.ofNullable(o.name())),
				Permission.STRING_PERMISSIONS_CODEC
						.optionalFieldOf("permissions")
						.forGetter(o -> Optional.ofNullable(o.permissions())),
				UserRole.MODIFIABLE_INTEGRATION_CODEC
						.optionalFieldOf("integrations", Collections.emptyMap())
						.forGetter(Request::integrations)
		).apply(inst, (name, permissions, integrations) -> new Request(name.orElse(null), permissions.orElse(null), integrations)));
	}
}
