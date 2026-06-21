package net.modgarden.backend.data.project;

import com.mojang.serialization.MapCodec;
import net.modgarden.backend.database.DatabaseAccess;

public interface SubmissionPlatform {
	String typeName();
	MapCodec<? extends SubmissionPlatform> getCodec();

	static <T extends SubmissionPlatform> MapCodec<SubmissionPlatform> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
