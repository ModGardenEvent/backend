package net.modgarden.backend.endpoint.v2.auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Map.entry;
import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/v2/auth/api_key")
public final class GenerateKeyEndpoint extends AuthEndpoint {
	public GenerateKeyEndpoint() {
		super("api_key", PermissionScope.ALL, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		if (!this.requirePermissions(ctx, scopePermissions, Permission.MODIFY_API_KEY)) return;

		Request<?> request = this.decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);
		if (request == null) return;

		if (Duration.between(Instant.now(), request.expires()).toDays() > 365 || Duration.between(Instant.now(), request.expires()).isNegative()) {
			ctx.status(400);
			ctx.result("API key expires too late or too early. It must expire at most in a year.");
			return;
		}

		byte[] uuid = UuidUtils.randomBytes();
		String apiKey = AuthEndpoint.generateAPIKey();
		String hash =
				AuthEndpoint.hashSecret(apiKey);

		Permissions requestedPermissions = request.permissions();
		String projectId = null;
		if (request.projectId().isPresent()) {
			projectId = request.projectId().get();
		}

		if (projectId != null) {
			try (
					var connection = this.getDatabaseConnection();
					var projectStatement = connection.prepareStatement("SELECT id FROM projects WHERE id = ?")
			) {
				projectStatement.setString(1, projectId);
				ResultSet resultSet = projectStatement.executeQuery();
				if (!resultSet.isBeforeFirst()) {
					ctx.status(404);
					ctx.result("Project with ID " + projectId + " does not exist");
					return;
				}
			}
		}

		switch (request.scope().id()) {
			case "project" -> {
				try (
						var connection = this.getDatabaseConnection();
						var permissionStatement = connection.prepareStatement("SELECT permissions FROM project_roles WHERE user_id = ? AND project_id = ?")
				) {
					permissionStatement.setString(1, userId);
					permissionStatement.setString(2, projectId);
					ResultSet resultSet = permissionStatement.executeQuery();
					Permissions projectPermissions = new Permissions(resultSet.getLong("permissions"));
					requestedPermissions = requestedPermissions.restrict(projectPermissions.bits());
					if (!this.requirePermissions(ctx, projectPermissions, Permission.MODIFY_API_KEY)) return;
				}
			}
			case "user" -> requestedPermissions = requestedPermissions.restrict(
					Permission.DEFAULT_USER_PERMISSIONS.bits() | scopePermissions.bits());
		}

		try (
				var connection = this.getDatabaseConnection();
				var apiKeyStatement = connection.prepareStatement("INSERT INTO api_keys(uuid, user_id, hash, expires, name) VALUES (?, ?, ?, ?, ?)")
		) {
			apiKeyStatement.setBytes(1, uuid);
			apiKeyStatement.setString(2, userId);
			apiKeyStatement.setString(3, hash);
			apiKeyStatement.setLong(4, request.expires().toEpochMilli());
			apiKeyStatement.setString(5, request.name());
			apiKeyStatement.execute();
		}

		try (
				var connection = this.getDatabaseConnection();
				var apiKeyScopeStatement = connection.prepareStatement("INSERT INTO api_key_scopes(uuid, scope, project_id, permissions) VALUES (?, ?, ?, ?)")
		) {
			apiKeyScopeStatement.setBytes(1, uuid);
			apiKeyScopeStatement.setString(2, request.scope().id());
			if (projectId != null) {
				apiKeyScopeStatement.setString(3, projectId);
			} else {
				// actually what the hell lmao. what is this second integer?
				apiKeyScopeStatement.setNull(3, 0);
			}
			apiKeyScopeStatement.setLong(4, requestedPermissions.bits());
			apiKeyScopeStatement.execute();
		}

		ctx.json(new Response(apiKey, UuidUtils.fromBytes(uuid)));
		ctx.status(200);
	}

	private interface Scope {
		Codec<Scope> CODEC = Codec.STRING.dispatch("scope", scope -> scope.getType().id(), string -> ScopeType.SCOPE_TYPES.get(string).getCodec());

		ScopeType<?> getType();
	}

	public record Request<T extends Scope>(
			ScopeType<T> scope,
			Optional<String> projectId,
			Permissions permissions,
			Instant expires,
			String name
	) {
		public static final Codec<Request<?>> CODEC = Scope.CODEC.xmap(
				scope -> {
					if (scope instanceof ProjectScope(String id, Permissions permissions, Instant expires, String name)) {
						return new Request<>(ProjectScope.TYPE, Optional.of(id), permissions, expires, name);
					} else if (scope instanceof UserScope(Permissions permissions, Instant expires, String name)) {
						return new Request<>(UserScope.TYPE, Optional.empty(), permissions, expires, name);
					} else {
						throw new IllegalStateException("unregistered scope type please do not let this ever happen");
					}
				},
				request -> {
					if (request.projectId().isPresent()) {
						return new ProjectScope(request.projectId().get(), request.permissions(), request.expires(), request.name());
					} else {
						return new UserScope(request.permissions(), request.expires(), request.name());
					}
				}
		);
	}

	public record Response(String apiKey, UUID uuid) {
		public static final Codec<Response> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("api_key").forGetter(Response::apiKey),
				ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(Response::uuid)
		).apply(inst, Response::new));
	}

	private record ScopeType<T extends Scope>(String id, MapCodec<T> codec) {
		public static final Map<String, ScopeType<?>> SCOPE_TYPES = Map.ofEntries(
				entry("project", ProjectScope.TYPE),
				entry("user", UserScope.TYPE)
		);

		public MapCodec<T> getCodec() {
			return codec;
		}
	}

	private record ProjectScope(String projectId, Permissions permissions, Instant expires, String name) implements Scope {
		public static final MapCodec<ProjectScope> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.fieldOf("project_id").forGetter(ProjectScope::projectId),
				Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(ProjectScope::permissions),
				ExtraCodecs.INSTANT_CODEC.fieldOf("expires").forGetter(ProjectScope::expires),
				Codec.STRING.fieldOf("name").forGetter(ProjectScope::name)
		).apply(inst, ProjectScope::new));
		public static final ScopeType<ProjectScope> TYPE = new ScopeType<>("project", ProjectScope.CODEC);

		@Override
		public ScopeType<ProjectScope> getType() {
			return TYPE;
		}
	}

	private record UserScope(Permissions permissions, Instant expires, String name) implements Scope {
		public static final MapCodec<UserScope> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(UserScope::permissions),
				ExtraCodecs.INSTANT_CODEC.fieldOf("expires").forGetter(UserScope::expires),
				Codec.STRING.fieldOf("name").forGetter(UserScope::name)
		).apply(inst, UserScope::new));
		public static final ScopeType<UserScope> TYPE = new ScopeType<>("user", UserScope.CODEC);

		@Override
		public ScopeType<UserScope> getType() {
			return TYPE;
		}
	}
}
