package net.modgarden.backend.data.event;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Metadata.fromMapCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Metadata;
import net.modgarden.backend.data.event.metadata.DraftMetadata;
import net.modgarden.backend.data.event.metadata.ModMetadata;
import net.modgarden.backend.data.user.User;

// TODO: Allow creating organisations, allow projects to be attributed to an organisation.
public record Project(String id,
					  Metadata metadata,
					  Map<String, String> team,
					  Map<String, Long> permissions,
					  List<String> submissions) {
	private static final Map<String, MapCodec<Metadata>> METADATA_MAP_CODECS = Map.ofEntries(
			entry("draft", fromMapCodec(DraftMetadata.CODEC)),
			entry("mod", fromMapCodec(ModMetadata.CODEC))
	);
	private static final Codec<Metadata> METADATA_CODEC = Codec.STRING.dispatch(Metadata::typeName, METADATA_MAP_CODECS::get);

	public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
			METADATA_CODEC.fieldOf("metadata").forGetter(Project::metadata),
			Codec.unboundedMap(User.ID_CODEC, Codec.STRING).fieldOf("team").forGetter(Project::team),
			Codec.unboundedMap(User.ID_CODEC, Codec.LONG).fieldOf("permissions").forGetter(Project::permissions),
			Codec.list(Submission.ID_CODEC).fieldOf("submissions").forGetter(Project::submissions)
    ).apply(inst, Project::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Project::validate);

	private static DataResult<String> validate(String id) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
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
