package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.platform.DownloadUrlPlatform;
import net.modgarden.backend.data.event.platform.ModrinthPlatform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Platform.fromMapCodec;

public record Submission(String id,
                         String event,
						 long timeSubmitted,
						 Project project,
						 Platform platform) {
	private static final Map<String, MapCodec<Platform>> PLATFORM_MAP_CODECS = Map.ofEntries(
			entry("modrinth", fromMapCodec(ModrinthPlatform.CODEC)),
			entry("download_url", fromMapCodec(DownloadUrlPlatform.CODEC))
	);
	private static final Codec<Platform> PLATFORM_CODEC = Codec.STRING.dispatch(Platform::getName, PLATFORM_MAP_CODECS::get);

	public static final Codec<Submission> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Event.ID_CODEC.fieldOf("event").forGetter(Submission::event),
			Codec.LONG.fieldOf("time_submitted").forGetter(Submission::timeSubmitted),
			Project.DIRECT_CODEC.fieldOf("project").forGetter(Submission::project),
			PLATFORM_CODEC.fieldOf("platform").forGetter(Submission::platform)
    ).apply(inst, Submission::new));
	public static final Codec<String> ID_CODEC = Codec.STRING.validate(Submission::validate);

	private static DataResult<String> validate(String id) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM submissions WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get project with id '" + id + "'.");
	}
}
