package net.modgarden.backend.data.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;

public record Event(String id,
                    String eventSlug,
                    String genreSlug,
                    String displayName,
                    Optional<String> discordRoleId,
                    String minecraftVersion,
                    String loader,
                    long registrationOpenTime,
                    long registrationCloseTime,
                    long startTime,
                    long endTime,
                    long freezeTime) {
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("event_slug").forGetter(Event::eventSlug),
			Codec.STRING.fieldOf("genre_slug").forGetter(Event::genreSlug),
			Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
			Codec.STRING.optionalFieldOf("discord_role_id").forGetter(Event::discordRoleId),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Event::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Event::loader),
			Codec.LONG.fieldOf("registration_open_time").forGetter(Event::registrationOpenTime),
			Codec.LONG.fieldOf("registration_close_time").forGetter(Event::registrationCloseTime),
            Codec.LONG.fieldOf("start_time").forGetter(Event::startTime),
			Codec.LONG.fieldOf("end_time").forGetter(Event::endTime),
			Codec.LONG.fieldOf("freeze_time").forGetter(Event::freezeTime)
    ).apply(inst, Event::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Event::validate);

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM themes WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get event with id '" + id + "'.");
	}
}
