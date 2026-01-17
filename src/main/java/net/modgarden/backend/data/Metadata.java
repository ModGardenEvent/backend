package net.modgarden.backend.data;

import com.mojang.serialization.MapCodec;

public interface Metadata {
	String typeName();
	MapCodec<? extends Metadata> codec();

	static <T extends Metadata> MapCodec<Metadata> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
