package net.modgarden.backend.data.project;

import com.mojang.serialization.MapCodec;

public interface ProjectMetadata {
	String typeName();
	MapCodec<? extends ProjectMetadata> codec();

	static <T extends ProjectMetadata> MapCodec<ProjectMetadata> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
