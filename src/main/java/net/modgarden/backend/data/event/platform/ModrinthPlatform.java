package net.modgarden.backend.data.event.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Platform;

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

	@Override
	public String getName() {
		return "modrinth";
	}

	@Override
	public MapCodec<ModrinthPlatform> getCodec() {
		return CODEC;
	}
}
