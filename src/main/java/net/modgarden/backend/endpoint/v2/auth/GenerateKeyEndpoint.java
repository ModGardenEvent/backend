package net.modgarden.backend.endpoint.v2.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import net.modgarden.backend.util.UuidUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.util.Map.entry;

@EndpointPath("/v2/auth/generate_key")
public class GenerateKeyEndpoint extends AuthEndpoint {
	public GenerateKeyEndpoint() {
		super("generate_key");
	}

	@Override
	public void handle(@NotNull Context ctx, String userId) throws Exception {
		DataResult<Pair<Request<?>, JsonElement>> requestResult = Request.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(ctx.body()));

		if (requestResult.isError()) {
			//noinspection OptionalGetWithoutIsPresent
			this.invalidBody(ctx, requestResult.error().get().message());
		}
		Request<?> request;
		try {
			request = requestResult.getOrThrow().getFirst();
		} catch (IllegalStateException e) {
			this.invalidBody(ctx, e.getMessage());
			return;
		}

		byte[] uuid = UuidUtils.randomBytes();
		String apiKey = AuthEndpoint.generateAPIKey();
		HashedSecret hashedSecret =
				AuthEndpoint.hashSecret(apiKey.getBytes(StandardCharsets.UTF_8));

		Permissions permissions = request.permissions();
		String projectId = null;
		if (request.projectId().isPresent()) {
			projectId = request.projectId().get();
		}

		if (projectId != null) {
			try (
					var connection = this.getDatabaseConnection();
					var permissionStatement = connection.prepareStatement("SELECT id FROM projects WHERE id = ?")
			) {
				permissionStatement.setString(1, projectId);
				ResultSet resultSet = permissionStatement.executeQuery();
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
						var permissionStatement = connection.prepareStatement("SELECT permissions FROM project_roles WHERE user_id = ?")
				) {
					permissionStatement.setString(1, userId);
					ResultSet resultSet = permissionStatement.executeQuery();
					permissions.and(resultSet.getLong("permissions"));
				}
			}
			case "user" -> {
				try (
						var connection = this.getDatabaseConnection();
						var permissionStatement = connection.prepareStatement("SELECT permissions FROM users WHERE id = ?")
				) {
					permissionStatement.setString(1, userId);
					ResultSet resultSet = permissionStatement.executeQuery();
					permissions.and(
							Permission.DEFAULT_USER_PERMISSIONS.getBits() | resultSet.getLong("permissions"));
				}
			}
		}

		try (
				var connection = this.getDatabaseConnection();
				var apiKeyStatement = connection.prepareStatement("INSERT INTO api_keys(uuid, user_id, salt, hash, expires) VALUES (?, ?, ?, ?, ?)")
		) {
			apiKeyStatement.setBytes(1, uuid);
			apiKeyStatement.setString(2, userId);
			apiKeyStatement.setBytes(3, hashedSecret.salt());
			apiKeyStatement.setBytes(4, hashedSecret.hash());
			apiKeyStatement.setLong(5, Instant.now().plus(Duration.ofDays(365)).toEpochMilli());
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
			apiKeyScopeStatement.setLong(4, request.permissions().getBits());
			apiKeyScopeStatement.execute();
		}

		ctx.json(new Response(apiKey));
		ctx.status(200);
	}

	private interface Scope {
		Codec<Scope> CODEC = Codec.STRING.dispatch("scope", scope -> scope.getType().id(), string -> ScopeType.SCOPE_TYPES.get(string).getCodec());

		ScopeType<?> getType();
	}

	public record Request<T extends Scope>(
			ScopeType<T> scope,
			Optional<String> projectId,
			Permissions permissions
	) {
		public static final Codec<Request<?>> CODEC = Scope.CODEC.xmap(
				scope -> {
					if (scope instanceof ProjectScope(String id, Permissions permissions)) {
						return new Request<>(ProjectScope.TYPE, Optional.of(id), permissions);
					} else if (scope instanceof UserScope(Permissions permissions)) {
						return new Request<>(UserScope.TYPE, Optional.empty(), permissions);
					} else {
						throw new IllegalStateException("unregistered scope type please do not let this ever happen");
					}
				},
				request -> {
					if (request.projectId().isPresent()) {
						return new ProjectScope(request.projectId().get(), request.permissions());
					} else {
						return new UserScope(request.permissions());
					}
				}
		);
	}

	public record Response(String apiKey) {
		public static final Codec<Response> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("api_key").forGetter(Response::apiKey)
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

	private record ProjectScope(String projectId, Permissions permissions) implements Scope {
		public static final MapCodec<ProjectScope> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.fieldOf("project_id").forGetter(ProjectScope::projectId),
				Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(ProjectScope::permissions)
		).apply(inst, ProjectScope::new));
		public static final ScopeType<ProjectScope> TYPE = new ScopeType<>("project", ProjectScope.CODEC);

		@Override
		public ScopeType<ProjectScope> getType() {
			return TYPE;
		}
	}

	private record UserScope(Permissions permissions) implements Scope {
		public static final MapCodec<UserScope> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(UserScope::permissions)
		).apply(inst, UserScope::new));
		public static final ScopeType<UserScope> TYPE = new ScopeType<>("user", UserScope.CODEC);

		@Override
		public ScopeType<UserScope> getType() {
			return TYPE;
		}
	}
}
