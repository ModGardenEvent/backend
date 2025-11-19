package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.util.ExtraCodecs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

// TODO: Allow creating organisations, allow projects to be attributed to an organisation.
public record Project(String id,
					  String slug,
					  ProjectMetadata metadata,
					  Map<String, String> team,
					  Map<String, String> permissions,
					  Map<String, Object> ext) {
	public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("slug").forGetter(Project::slug),
			ProjectMetadata.CODEC.fieldOf("metadata").forGetter(Project::metadata),
			Codec.unboundedMap(User.ID_CODEC, Codec.STRING).fieldOf("team").forGetter(Project::team),
			Codec.unboundedMap(User.ID_CODEC, Codec.STRING).fieldOf("permissions").forGetter(Project::permissions),
			ExtraCodecs.EXT_CODEC.fieldOf("ext").forGetter(Project::ext)
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

	public record ProjectMetadata(String modId, String name, String description,
								  String sourceUrl, String iconUrl, String bannerUrl) {
		public static final Codec<ProjectMetadata> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("mod_id").forGetter(ProjectMetadata::modId),
				Codec.STRING.fieldOf("name").forGetter(ProjectMetadata::name),
				Codec.STRING.fieldOf("description").forGetter(ProjectMetadata::description),
				Codec.STRING.fieldOf("source_url").forGetter(ProjectMetadata::sourceUrl),
				Codec.STRING.fieldOf("icon_url").forGetter(ProjectMetadata::iconUrl),
				Codec.STRING.fieldOf("banner_url").forGetter(ProjectMetadata::bannerUrl)
		).apply(inst, ProjectMetadata::new));
	}
}
