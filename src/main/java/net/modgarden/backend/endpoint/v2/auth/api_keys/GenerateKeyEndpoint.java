package net.modgarden.backend.endpoint.v2.auth.api_keys;

import static java.util.Map.entry;
import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.BadRequestException;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import net.modgarden.backend.util.codec.ExtraCodecs;
import net.modgarden.backend.util.UuidUtils;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(POST)
@EndpointPath("/v2/auth/api-keys")
public final class GenerateKeyEndpoint extends AuthEndpoint {
	public GenerateKeyEndpoint() {
		super("api-keys", PermissionScope.ALL, true);
	}

	@Override
	public Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		Request<?> request = this.decodeBody(ctx, Request.CODEC);

		if (Duration.between(Instant.now(), request.expires()).toDays() > 365 || Duration.between(Instant.now(), request.expires()).isNegative()) {
			throw new BadRequestException("API key expires too late or too early. It must expire at most in a year.");
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

		DatabaseAccess db = DatabaseAccess.get();

		if (projectId != null && !db.projectExists(projectId)) {
			throw new NotFoundException("Project with ID " + projectId + " does not exist");
		}

		switch (request.scope().id()) {
			case "project" -> {
				Permissions projectPermissions = db.getProjectMemberPermissions(userId, projectId);
				requestedPermissions = requestedPermissions.restrictTo(projectPermissions.bits());
				this.requireAllPermissions(projectPermissions, Permission.MODIFY_API_KEY);
			}
			case "user" -> requestedPermissions = requestedPermissions.restrictTo(scopePermissions.bits());
		}

		db.createApiKey(uuid, userId, hash, request.expires(), request.name());
		db.createApiKeyScope(uuid, request.scope().id(), projectId, requestedPermissions);

		return Response.ok(new ApiKeyResponse(apiKey, UuidUtils.fromBytes(uuid)));
	}

	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.MODIFY_API_KEY);
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
						throw new IllegalStateException("Unregistered scope type. Please do not let this ever happen.");
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

	public record ApiKeyResponse(String apiKey, UUID uuid) {
		public static final Codec<ApiKeyResponse> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("api_key").forGetter(ApiKeyResponse::apiKey),
				ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(ApiKeyResponse::uuid)
		).apply(inst, ApiKeyResponse::new));
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
