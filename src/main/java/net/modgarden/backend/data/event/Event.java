package net.modgarden.backend.data.event;

import static java.util.Map.entry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.game.MinecraftEventPlatform;

public record Event(String id,
					String slug,
					EventMetadata metadata,
					EventTimes times,
					EventPlatform platform,
					EventRoles roles) {
	private static final Map<String, MapCodec<EventPlatform>> PLATFORM_MAP_CODECS = Map.ofEntries(
			entry(MinecraftEventPlatform.ID, EventPlatform.fromMapCodec(MinecraftEventPlatform.CODEC))
	);
	private static final Codec<EventPlatform> PLATFORM_CODEC = Codec.STRING.dispatch("game", EventPlatform::game, PLATFORM_MAP_CODECS::get);
	public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
			Codec.STRING.fieldOf("slug").forGetter(Event::slug),
			EventMetadata.CODEC.fieldOf("metadata").forGetter(Event::metadata),
			EventTimes.CODEC.fieldOf("times").forGetter(Event::times),
			PLATFORM_CODEC.fieldOf("platform").forGetter(Event::platform),
			EventRoles.CODEC.optionalFieldOf("roles", new EventRoles(null, null, null)).forGetter(Event::roles)
	).apply(inst, Event::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Event::validate);

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM events WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get event with id '" + id + "'");
	}
}
