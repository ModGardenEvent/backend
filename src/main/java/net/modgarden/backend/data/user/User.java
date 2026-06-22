package net.modgarden.backend.data.user;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Integration.fromCodec;

import java.time.Instant;
import java.util.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.project.Project;
import net.modgarden.backend.data.user.integration.DiscordUserIntegration;
import net.modgarden.backend.data.user.integration.MinecraftUserIntegration;
import net.modgarden.backend.data.user.integration.ModrinthUserIntegration;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.util.codec.ExtraCodecs;
import net.modgarden.backend.util.codec.NullableCodec;
import net.modgarden.backend.util.NullableWrapper;

public record User(
		String id,
		String username,
		Bio bio,
		Permissions permissions,
		Instant created,
		Map<String, Integration> integrations,
		Set<String> projects,
		Set<String> events,
		Set<String> roles
) {
	private static final Map<String, Codec<Integration>> INTEGRATION_CODECS = Map.ofEntries(
			entry(ModrinthUserIntegration.ID, fromCodec(ModrinthUserIntegration.CODEC)),
			entry(DiscordUserIntegration.ID, fromCodec(DiscordUserIntegration.CODEC)),
			entry(MinecraftUserIntegration.ID, fromCodec(MinecraftUserIntegration.CODEC))
	);
	private static final Codec<String> INTEGRATION_CODEC_KEY = Codec.STRING.validate(key -> {
		if (!INTEGRATION_CODECS.containsKey(key)) {
			return DataResult.error(() -> "Integration type '" + key + "' does not exist for users");
		}
		return DataResult.success(key);
	});
	public static final Codec<Map<String, Integration>> INTEGRATION_CODEC = Codec.dispatchedMap(INTEGRATION_CODEC_KEY, INTEGRATION_CODECS::get);
	public static final Codec<Map<String, NullableWrapper<Integration>>> MODIFIABLE_INTEGRATION_CODEC = Codec.dispatchedMap(INTEGRATION_CODEC_KEY,
			key -> NullableCodec.nullable(INTEGRATION_CODECS.get(key)));

    public static final Codec<User> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("username").forGetter(User::username),
			Bio.DIRECT_CODEC.fieldOf("bio").forGetter(User::bio),
			Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(User::permissions),
			ExtraCodecs.INSTANT_CODEC.fieldOf("created").forGetter(User::created),
		    INTEGRATION_CODEC.fieldOf("integrations").forGetter(User::integrations),
		    Project.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("projects")
				    .forGetter(User::projects),
		    Event.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("events")
				    .forGetter(User::events),
		    UserRole.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("roles")
				    .forGetter(User::roles)
    ).apply(inst, User::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(User::validateId);
	public static final Codec<String> NEW_USERNAME_CODEC = Codec.STRING
			.xmap(s -> s.toLowerCase(Locale.ROOT), s -> s)
			.validate(User::validateNewUsername);

	private static DataResult<String> validateId(String id) {
		DatabaseAccess db = DatabaseAccess.get();

		if (db.logIfThrown(() -> db.userIdExists(id))) {
			return DataResult.success(id);
		} else {
			return DataResult.error(() -> "Failed to get user with id '" + id + "'.");
		}
    }

	private static DataResult<String> validateNewUsername(String username) {
		DatabaseAccess db = DatabaseAccess.get();

		if (db.logIfThrown(() -> !db.usernameExists(username))) {
			return DataResult.success(username);
		} else {
			return DataResult.error(() -> "User with username '" + username + "' already exists.");
		}
	}
}
