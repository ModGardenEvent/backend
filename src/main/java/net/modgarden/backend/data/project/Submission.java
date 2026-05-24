package net.modgarden.backend.data.project;

import static java.util.Map.entry;
import static net.modgarden.backend.data.project.SubmissionPlatform.fromMapCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.project.platform.DownloadUrlSubmissionPlatform;
import net.modgarden.backend.data.project.platform.ModrinthSubmissionPlatform;
import net.modgarden.backend.util.codec.ExtraCodecs;

public record Submission(String id,
                         String event,
						 Instant timeSubmitted,
						 Project project,
						 SubmissionPlatform platform) {
	private static final Map<String, MapCodec<SubmissionPlatform>> PLATFORM_MAP_CODECS = Map.ofEntries(
			entry(ModrinthSubmissionPlatform.ID, fromMapCodec(ModrinthSubmissionPlatform.CODEC)),
			entry(DownloadUrlSubmissionPlatform.ID, fromMapCodec(DownloadUrlSubmissionPlatform.CODEC))
	);
	private static final Codec<String> PLATFORM_KEY_CODEC = Codec.STRING.validate(key -> {
		if (!PLATFORM_MAP_CODECS.containsKey(key)) {
			return DataResult.error(() -> "EventPlatform type '" + key + "' does not exist");
		}
		return DataResult.success(key);
	});
	public static final Codec<SubmissionPlatform> PLATFORM_CODEC = PLATFORM_KEY_CODEC.dispatch(SubmissionPlatform::typeName, PLATFORM_MAP_CODECS::get);

	public static final Codec<Submission> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Event.ID_CODEC.fieldOf("event_id").forGetter(Submission::event),
			ExtraCodecs.INSTANT_CODEC.fieldOf("time_submitted").forGetter(Submission::timeSubmitted),
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
		return DataResult.error(() -> "Failed to get project with id '" + id + "'");
	}
}
