package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// TODO: Allow creating organisations, allow projects to be attributed to an organisation.
public record Project(String id,
					  Type type,
					  Metadata metadata,
					  Map<String, String> team,
					  Map<String, Long> permissions,
					  List<String> submissions,
					  Map<String, Object> ext) {
	public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Type.CODEC.fieldOf("type").forGetter(Project::type),
			Metadata.CODEC.fieldOf("metadata").forGetter(Project::metadata),
			Codec.unboundedMap(User.ID_CODEC, Codec.STRING).fieldOf("team").forGetter(Project::team),
			Codec.unboundedMap(User.ID_CODEC, Codec.LONG).fieldOf("permissions").forGetter(Project::permissions),
			Codec.list(Submission.ID_CODEC).fieldOf("submissions").forGetter(Project::submissions),
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

	public record Metadata(String modId, String name, @Nullable String description,
						   String sourceUrl, String iconUrl, @Nullable String bannerUrl) {
		public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("mod_id").forGetter(Metadata::modId),
				Codec.STRING.fieldOf("name").forGetter(Metadata::name),
				Codec.STRING.optionalFieldOf("description").forGetter(Metadata::descriptionAsOptional),
				Codec.STRING.fieldOf("source_url").forGetter(Metadata::sourceUrl),
				Codec.STRING.fieldOf("icon_url").forGetter(Metadata::iconUrl),
				Codec.STRING.optionalFieldOf("banner_url").forGetter(Metadata::bannerUrlAsOptional)
		).apply(inst, Metadata::new));

		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private Metadata(String modId, String name, Optional<String> description, String sourceUrl, String iconUrl, Optional<String> bannerUrl) {
			this(modId, name, description.orElse(null), sourceUrl, iconUrl, bannerUrl.orElse(null));
		}

		private Optional<String> descriptionAsOptional() {
			return Optional.ofNullable(description);
		}

		private Optional<String> bannerUrlAsOptional() {
			return Optional.ofNullable(bannerUrl);
		}
	}

	public enum Type {
		MOD("mod"),;

		public static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(string -> {
			Type type = fromString(string);
			return type == null ? DataResult.error(() -> "Could not find project type '" + string + "'.") :
					DataResult.success(type);
		}, Type::getName);

		private final String name;

		Type(String name) {
			this.name = name;
		}

		public static Type fromString(String value) {
			for (Type type : Type.values()) {
				if (type.getName().equals(value)) {
					return type;
				}
			}
			return null;
		}

		public String getName() {
			return name;
		}
	}
}
