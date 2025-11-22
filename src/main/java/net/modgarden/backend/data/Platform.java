package net.modgarden.backend.data;

import com.mojang.serialization.MapCodec;

public interface Platform {
	String getName();
	MapCodec<? extends Platform> getCodec();

	static <T extends Platform> MapCodec<Platform> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
