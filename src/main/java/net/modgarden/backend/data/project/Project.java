package net.modgarden.backend.data.project;

import static java.util.Map.entry;
import static net.modgarden.backend.data.project.ProjectMetadata.fromMapCodec;

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
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.project.metadata.NoneProjectMetadata;
import net.modgarden.backend.data.project.metadata.ModProjectMetadata;
import net.modgarden.backend.data.user.User;

// TODO: Allow creating organisations, allow projects to be attributed to an organisation.
public record Project(String id,
					  ProjectMetadata metadata,
					  Map<String, String> team,
					  Map<String, Permissions> permissions,
					  List<String> submissions) {
	private static final Map<String, MapCodec<ProjectMetadata>> METADATA_MAP_CODECS = Map.ofEntries(
			entry("none", fromMapCodec(NoneProjectMetadata.CODEC)),
			entry("mod", fromMapCodec(ModProjectMetadata.CODEC))
	);
	private static final Codec<ProjectMetadata> METADATA_CODEC = Codec.STRING.dispatch(ProjectMetadata::typeName, METADATA_MAP_CODECS::get);

	public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
			METADATA_CODEC.fieldOf("metadata").forGetter(Project::metadata),
			Codec.unboundedMap(User.ID_CODEC, Codec.STRING).fieldOf("team").forGetter(Project::team),
			Codec.unboundedMap(User.ID_CODEC, Permission.STRING_PERMISSIONS_CODEC).fieldOf("permissions").forGetter(Project::permissions),
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
		return DataResult.error(() -> "Failed to get project with id '" + id + "'");
	}
}
