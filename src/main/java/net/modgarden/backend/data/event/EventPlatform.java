package net.modgarden.backend.data.event;

import com.mojang.serialization.MapCodec;

public interface EventPlatform {
	String game();

	MapCodec<? extends EventPlatform> getCodec();

	static <T extends EventPlatform> MapCodec<EventPlatform> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T) metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
