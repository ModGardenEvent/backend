package net.modgarden.backend.data.event.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Platform;

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
public record DownloadUrlPlatform(String downloadUrl) implements Platform {
	public static final MapCodec<DownloadUrlPlatform> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("download_url").forGetter(DownloadUrlPlatform::downloadUrl)
	).apply(inst, DownloadUrlPlatform::new));

	@Override
	public String getName() {
		return "download_url";
	}

	@Override
	public MapCodec<DownloadUrlPlatform> getCodec() {
		return CODEC;
	}
}
