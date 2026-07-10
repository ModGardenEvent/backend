package net.modgarden.backend.data.project.platform;

import com.mojang.serialization.MapCodec;
import net.modgarden.backend.data.project.SubmissionPlatform;

/// A blank platform in case the platform data gets corrupted.
///
/// An example would be as follows.
/// ```json
/// {
/// 	"type": "none"
/// }
/// ```
///
public record NoneSubmissionPlatform() implements SubmissionPlatform {
	public static final String ID = "none";
	public static final NoneSubmissionPlatform INSTANCE = new NoneSubmissionPlatform();
	public static final MapCodec<NoneSubmissionPlatform> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public String typeName() {
		return ID;
	}

	@Override
	public MapCodec<NoneSubmissionPlatform> getCodec() {
		return CODEC;
	}
}
