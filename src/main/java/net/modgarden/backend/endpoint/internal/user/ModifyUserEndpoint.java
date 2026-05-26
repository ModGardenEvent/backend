package net.modgarden.backend.endpoint.internal.user;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.user.Bio;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.data.user.integration.DiscordUserIntegration;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.AlreadyExistsException;
import net.modgarden.backend.endpoint.internal.InternalEndpoint;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.RemovableValue;
import net.modgarden.backend.util.codec.RemovableValueCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.PATCH;

@EndpointMethod(PATCH)
@EndpointPath("/internal/user/modify/{user_id}")
public class ModifyUserEndpoint extends InternalEndpoint {
	public ModifyUserEndpoint() {
		super("user/modify/{user_id}");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String userIdToModify = ctx.pathParam("user_id");

		User user = db.getUserFromId(userIdToModify);

		if (request.username != null) {
			String username = request.username;
			boolean sameUsername = username.equals(user.username());

			if (!sameUsername && db.usernameExists(username)) {
				throw new AlreadyExistsException("Username '" + username + "' is already in use");
			}

			// Allow result to go through anyways in the case of the same username.
			if (!sameUsername) {
				db.setUsername(userIdToModify, username);
			}
		}

		if (request.bio != null) {
			if (request.bio.displayName() != null) {
				db.setUserBioDisplayName(userIdToModify, request.bio.displayName().value());
			}
			if (request.bio.pronouns() != null) {
				db.setUserBioPronouns(userIdToModify, request.bio.pronouns().value());
			}
			if (request.bio.description() != null) {
				db.setUserBioDescription(userIdToModify, request.bio.description().value());
			}
			if (request.bio.fields() != null) {
				for (Map.Entry<String, NullableWrapper<String>> entry : request.bio.fields().entrySet()) {
					handleUserBioField(db, userIdToModify, entry.getKey(), entry.getValue().value());
				}
			}
		}

		if (!request.integrations.isEmpty()) {
			for (Map.Entry<String, NullableWrapper<Integration>> entry : request.integrations.entrySet()) {
				handleIntegration(db, userIdToModify, entry.getKey(), entry.getValue().value());
			}
		}

		if (!request.roles.isEmpty()) {
			for (RemovableValue<String> role : request.roles) {
				if (role.remove()) {
					db.removeUserRole(role.value(), userIdToModify);
					continue;
				}

				boolean hasRole = user.roles().contains(role.value());

				if (hasRole)
					continue;

				db.addUserRole(role.value(), userIdToModify);
			}
		}

		return Response.ok();
	}

	private static void handleUserBioField(DatabaseAccess db,
	                                       String userId,
										   String fieldName,
	                                       @Nullable String fieldValue) throws SQLException {
		if (fieldValue != null) {
			db.addUserBioField(userId, fieldName, fieldValue);
			return;
		}
		db.removeUserBioField(userId, fieldName);
	}

	private static void handleIntegration(DatabaseAccess db,
										  String userId,
										  String key,
										  @Nullable Integration integration) throws SQLException {
		if (key.equals(DiscordUserIntegration.ID)) {
			if (integration instanceof DiscordUserIntegration(String discordId)) {
				db.setUserDiscordIntegration(userId, discordId);
				return;
			}
			db.removeUserDiscordIntegration(userId);
		}
		// TODO: Implement Modrinth and Minecraft later, when we actually need them.
	}

	public record Request(@Nullable String username,
	                      @Nullable Bio.Modifiable bio,
	                      Map<String, NullableWrapper<Integration>> integrations,
	                      List<RemovableValue<String>> roles) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING
						.optionalFieldOf("username")
						.forGetter(request -> Optional.ofNullable(request.username)),
				Bio.Modifiable.CODEC
						.optionalFieldOf("bio")
						.forGetter(request -> Optional.ofNullable(request.bio)),
				User.MODIFIABLE_INTEGRATION_CODEC
						.optionalFieldOf("integrations", Collections.emptyMap()).
						forGetter(Request::integrations),
				RemovableValueCodec.removable(UserRole.ID_CODEC)
						.listOf()
						.optionalFieldOf("roles", Collections.emptyList())
						.forGetter(Request::roles)
		).apply(inst, (username, bio, integrations, roles) ->
				new Request(username.orElse(null), bio.orElse(null), integrations, roles)));
	}
}
