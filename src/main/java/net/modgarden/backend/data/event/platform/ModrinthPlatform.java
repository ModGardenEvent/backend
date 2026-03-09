package net.modgarden.backend.data.event.platform;

import static net.modgarden.backend.util.HandleFinder.findHandle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.Connection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Metadata;
import net.modgarden.backend.data.Platform;
import net.modgarden.backend.data.event.metadata.ModMetadata;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.util.MetadataUtils;

/// A platform for Modrinth releases, linking to a specific version as part of a Modrinth project.
///
/// An example based on Variant Lib would be as follows.
/// ```json
/// {
/// 	"type": "modrinth",
/// 	"project_id": "LQCrGzOR",
/// 	"version_id": "Qt7I0urr"
/// }
/// ```
///
/// @param projectId	The project ID of the Modrinth project.
/// @param versionId	The version ID to pull from Modrinth for the mod JAR.
///
public record ModrinthPlatform(String projectId, String versionId) implements Platform {
	public static final MapCodec<ModrinthPlatform> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("project_id").forGetter(ModrinthPlatform::projectId),
			Codec.STRING.fieldOf("version_id").forGetter(ModrinthPlatform::versionId)
	).apply(inst, ModrinthPlatform::new));
	private static final MethodHandle GET_CONNECTION =
			findHandle(lookup -> MethodHandles.privateLookupIn(DatabaseAccess.class, lookup)
					.findVirtual(DatabaseAccess.class, "getConnection", MethodType.methodType(Connection.class)))
					.orElseThrow();

	@Override
	public String typeName() {
		return "modrinth";
	}

	@Override
	public MapCodec<ModrinthPlatform> getCodec() {
		return CODEC;
	}

	@Override
	public void addToDatabase(DatabaseAccess db, String gardenProjectId, String submissionId) throws Exception {
		// TODO: migrate to DatabaseAccess
		Connection connection;

		try {
			connection = (Connection) GET_CONNECTION.invokeExact(db);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}

		try (
				var submissionTypeModrinthStatement = connection.prepareStatement("""
					INSERT INTO submission_type_modrinth (submission_id, modrinth_id, version_id)
					VALUES (?, ?, ?)
				""");
				var projectModMetadataStatement = connection.prepareStatement("""
					INSERT INTO project_mod_metadata (project_id, mod_id, name, description, source_url)
					VALUES (?, ?, ?, ?, ?)
				""")
		) {
			submissionTypeModrinthStatement.setString(1, submissionId);
			submissionTypeModrinthStatement.setString(2, projectId);
			submissionTypeModrinthStatement.setString(3, versionId);
			submissionTypeModrinthStatement.executeUpdate();

			Metadata metadata = MetadataUtils.getMetadataFromModrinth(projectId, versionId);
			if (metadata instanceof ModMetadata(String modId, String name, String description, String sourceUrl)) {
				projectModMetadataStatement.setString(1, gardenProjectId);
				projectModMetadataStatement.setString(2, modId);
				projectModMetadataStatement.setString(3, name);
				projectModMetadataStatement.setString(4, description);
				projectModMetadataStatement.setString(5, sourceUrl);
				projectModMetadataStatement.executeUpdate();
			} else {
				throw new UnsupportedOperationException("Unsupported metadata type for Modrinth platform '" + metadata.typeName() + "'");
			}
		}
	}
}
