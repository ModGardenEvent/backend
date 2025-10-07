package net.modgarden.backend.data.user;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.user.integration.DiscordIntegration;
import net.modgarden.backend.data.user.integration.MinecraftIntegration;
import net.modgarden.backend.data.user.integration.ModrinthIntegration;
import net.modgarden.backend.util.ExtraCodecs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Integration.fromCodec;

public record User(
		String id,
		String username,
		Instant created,
		List<String> projects,
		List<String> events,
		List<AwardInstance.UserValues> awards,
		Permissions permissions,
		Map<String, Integration> integrations
) {
	public static final String USERNAME_REGEX = "^(?=.{3,32}$)[a-z_0-9]+?$";
	private static final Map<String, Codec<Integration>> INTEGRATION_CODECS = Map.ofEntries(
			entry("modrinth", fromCodec(ModrinthIntegration.CODEC)),
			entry("discord", fromCodec(DiscordIntegration.CODEC)),
			entry("minecraft", fromCodec(MinecraftIntegration.CODEC))
	);

    public static final Codec<User> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("username").forGetter(User::username),
		    ExtraCodecs.INSTANT_CODEC.fieldOf("created").forGetter(User::created),
            Project.ID_CODEC.listOf().fieldOf("projects").forGetter(User::projects),
            Event.ID_CODEC.listOf().fieldOf("events").forGetter(User::events),
            AwardInstance.UserValues.CODEC.listOf().fieldOf("awards").forGetter(User::awards),
			Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(User::permissions),
		    Codec.dispatchedMap(Codec.STRING, INTEGRATION_CODECS::get).fieldOf("integrations").forGetter(User::integrations)
    ).apply(inst, User::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(User::validate);

	private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result != null && result.getBoolean(1))
                return DataResult.success(id);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return DataResult.error(() -> "Failed to get user with id '" + id + "'.");
    }
}
