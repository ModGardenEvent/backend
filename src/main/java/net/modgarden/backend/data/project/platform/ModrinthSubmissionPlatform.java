package net.modgarden.backend.data.project.platform;

import static net.modgarden.backend.util.HandleFinder.findHandle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.Connection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.project.ProjectMetadata;
import net.modgarden.backend.data.project.SubmissionPlatform;
import net.modgarden.backend.data.project.metadata.ModProjectMetadata;
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
public record ModrinthSubmissionPlatform(String projectId, String versionId) implements SubmissionPlatform {
	public static final String ID = "modrinth";
	public static final MapCodec<ModrinthSubmissionPlatform> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("project_id").forGetter(ModrinthSubmissionPlatform::projectId),
			Codec.STRING.fieldOf("version_id").forGetter(ModrinthSubmissionPlatform::versionId)
	).apply(inst, ModrinthSubmissionPlatform::new));
	private static final MethodHandle GET_CONNECTION =
			findHandle(lookup -> MethodHandles.privateLookupIn(DatabaseAccess.class, lookup)
					.findVirtual(DatabaseAccess.class, "getConnection", MethodType.methodType(Connection.class)))
					.orElseThrow();

	@Override
	public String typeName() {
		return "modrinth";
	}

	@Override
	public MapCodec<ModrinthSubmissionPlatform> getCodec() {
		return CODEC;
	}
}
