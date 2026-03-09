package net.modgarden.backend.endpoint.v2.auth.api_key;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.FallibleFunction;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/auth/api_key")
public final class ListKeysEndpoint extends AuthEndpoint {
	public ListKeysEndpoint() {
		super("api_key", PermissionScope.ALL, false);
	}

	@Override
	public void onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String projectId = ctx.queryParam("project_id");
		FallibleFunction<UUID, Optional<DatabaseAccess.ApiKeyScope>, SQLException> query;
		if (projectId == null) {
			query = db::getApiKeyScope;
		} else {
			query = (uuid) -> db.getApiKeyScope(uuid, projectId);
		}

		List<ApiKey> apiKeys = new ArrayList<>();
		Collection<DatabaseAccess.ApiKey> resultSet = db.getApiKeys(userId);
		for (DatabaseAccess.ApiKey apiKey : resultSet) {
			Optional<DatabaseAccess.ApiKeyScope> optionalApiKeyScope = query.apply(apiKey.uuid());
			if (optionalApiKeyScope.isEmpty()) {
				continue;
			}

			DatabaseAccess.ApiKeyScope apiKeyScope = optionalApiKeyScope.get();
			apiKeys.add(new ApiKey(
					apiKey.uuid(),
					apiKeyScope.permissions(),
					apiKey.expires(),
					apiKeyScope.scope(),
					Optional.ofNullable(apiKeyScope.projectId()),
					apiKey.name()
			));
		}

		ctx.json(new Response(apiKeys));
		ctx.status(200);
	}

	public record Response(List<ApiKey> apiKeys) {
		public static final Codec<Response> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.list(ApiKey.CODEC).fieldOf("api_keys").forGetter(Response::apiKeys)
		).apply(inst, Response::new));
	}

	public record ApiKey(
			UUID uuid,
			Permissions permissions,
			Instant expires,
			PermissionScope scope,
			Optional<String> projectId,
			String name
	) {
		public static final Codec<ApiKey> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(ApiKey::uuid),
				Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(ApiKey::permissions),
				ExtraCodecs.INSTANT_CODEC.fieldOf("expires").forGetter(ApiKey::expires),
				PermissionScope.CODEC.fieldOf("scope").forGetter(ApiKey::scope),
				Codec.STRING.optionalFieldOf("project_id").forGetter(ApiKey::projectId),
				Codec.STRING.fieldOf("name").forGetter(ApiKey::name)
		).apply(inst, ApiKey::new));
	}
}
