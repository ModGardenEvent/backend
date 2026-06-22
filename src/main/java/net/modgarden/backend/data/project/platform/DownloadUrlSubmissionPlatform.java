package net.modgarden.backend.data.project.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.project.SubmissionPlatform;
import net.modgarden.backend.database.DatabaseAccess;

/// A platform for download URLs, useful for Git Releases without depending on a specific Git host.
///
/// An example based on Variant Lib would be as follows.
/// ```json
/// {
/// 	"type": "download_url",
/// 	"download_url": "https://git.greenhouse.lgbt/Modding/variant-lib/releases/download/0.3.2+1.21.5/variantlib-fabric-0.3.2+1.21.5.jar"
/// }
/// ```
///
/// @param downloadUrl A direct download link to a mod JAR.
///
public record DownloadUrlSubmissionPlatform(String downloadUrl) implements SubmissionPlatform {
	public static final String ID = "download_url";
	public static final MapCodec<DownloadUrlSubmissionPlatform> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("download_url").forGetter(DownloadUrlSubmissionPlatform::downloadUrl)
	).apply(inst, DownloadUrlSubmissionPlatform::new));

	@Override
	public String typeName() {
		return "download_url";
	}

	@Override
	public MapCodec<DownloadUrlSubmissionPlatform> getCodec() {
		return CODEC;
	}
}
