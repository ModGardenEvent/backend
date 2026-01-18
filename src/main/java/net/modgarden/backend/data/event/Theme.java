package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public record Theme(String id,
                    String themeSlug,
                    String eventSlug,
                    String displayName,
                    Optional<String> discordRoleId,
                    String minecraftVersion,
                    String loader,
                    long registrationOpenTime,
                    long registrationCloseTime,
                    long startTime,
                    long endTime,
                    long freezeTime) {
    public static final Codec<Theme> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Theme::id),
            Codec.STRING.fieldOf("theme_slug").forGetter(Theme::themeSlug),
			Codec.STRING.fieldOf("event_slug").forGetter(Theme::eventSlug),
			Codec.STRING.fieldOf("display_name").forGetter(Theme::displayName),
			Codec.STRING.optionalFieldOf("discord_role_id").forGetter(Theme::discordRoleId),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Theme::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Theme::loader),
			Codec.LONG.fieldOf("registration_open_time").forGetter(Theme::registrationOpenTime),
			Codec.LONG.fieldOf("registration_close_time").forGetter(Theme::registrationCloseTime),
            Codec.LONG.fieldOf("start_time").forGetter(Theme::startTime),
			Codec.LONG.fieldOf("end_time").forGetter(Theme::endTime),
			Codec.LONG.fieldOf("freeze_time").forGetter(Theme::freezeTime)
    ).apply(inst, Theme::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Theme::validate);

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
		return DataResult.error(() -> "Failed to get event with id '" + id + "'.");
	}
}
