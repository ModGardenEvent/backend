package net.modgarden.backend.data.user;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Integration.fromCodec;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.user.integration.DiscordIntegration;
import net.modgarden.backend.data.user.integration.MinecraftIntegration;
import net.modgarden.backend.data.user.integration.ModrinthIntegration;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.util.ExtraCodecs;

public record User(
		String id,
		String username,
		Instant created,
		Permissions permissions,
		Map<String, Integration> integrations,
		Set<String> projects,
		Set<String> events,
		Set<String> awards,
		Set<String> roles
) {
	private static final Map<String, Codec<Integration>> INTEGRATION_CODECS = Map.ofEntries(
			entry("modrinth", fromCodec(ModrinthIntegration.CODEC)),
			entry("discord", fromCodec(DiscordIntegration.CODEC)),
			entry("minecraft", fromCodec(MinecraftIntegration.CODEC))
	);

    public static final Codec<User> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("username").forGetter(User::username),
		    ExtraCodecs.INSTANT_CODEC.fieldOf("created").forGetter(User::created),
			Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(User::permissions),
		    Codec.dispatchedMap(Codec.STRING, INTEGRATION_CODECS::get).fieldOf("integrations").forGetter(User::integrations),
		    Project.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("projects")
				    .forGetter(User::projects),
		    Event.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("events")
				    .forGetter(User::events),
		    Award.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("awards")
				    .forGetter(User::awards),
		    UserRole.ID_CODEC.listOf()
				    .xmap(list -> (Set<String>) new HashSet<>(list), set -> List.of(set.toArray(String[]::new)))
				    .fieldOf("roles")
				    .forGetter(User::roles)
    ).apply(inst, User::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(User::validate);

	private static DataResult<String> validate(String id) {
		DatabaseAccess db = DatabaseAccess.get();

		if (db.logIfThrown(() -> db.userExists(id))) {
			return DataResult.success(id);
		} else {
			return DataResult.error(() -> "Failed to get user with id '" + id + "'.");
		}
    }
}
