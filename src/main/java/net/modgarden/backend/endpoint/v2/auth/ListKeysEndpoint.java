package net.modgarden.backend.endpoint.v2.auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.UuidUtils;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

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
		String projectId = ctx.queryParam("project_id");
		String query;
		if (projectId == null) {
			query = "SELECT scope, project_id, permissions FROM api_key_scopes WHERE uuid = ?";
		} else {
			query = "SELECT scope, project_id, permissions FROM api_key_scopes WHERE uuid = ? AND project_id = ?";
		}

		List<ApiKey> apiKeys = new ArrayList<>();
		try (
				var connection = this.getDatabaseConnection();
				var apiKeyStatement = connection.prepareStatement("SELECT uuid, expires, name FROM api_keys WHERE user_id = ?");
				var apiKeyScopeStatement = connection.prepareStatement(query)
		) {
			apiKeyStatement.setString(1, userId);
			ResultSet resultSet = apiKeyStatement.executeQuery();
			while (resultSet.next()) {
				UUID uuid = UuidUtils.fromBytes(resultSet.getBytes("uuid"));
				apiKeyScopeStatement.setBytes(1, resultSet.getBytes("uuid"));
				if (projectId != null) {
					apiKeyScopeStatement.setString(2, projectId);
				}
				ResultSet scopeResult = apiKeyScopeStatement.executeQuery();
				if (!scopeResult.isBeforeFirst()) {
					continue;
				}

				apiKeys.add(new ApiKey(
						uuid,
						new Permissions(scopeResult.getLong("permissions")),
						Instant.ofEpochMilli(resultSet.getLong("expires")),
						PermissionScope.fromString(scopeResult.getString("scope")),
						Optional.ofNullable(scopeResult.getString("project_id")),
						resultSet.getString("name")
				));
			}
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
