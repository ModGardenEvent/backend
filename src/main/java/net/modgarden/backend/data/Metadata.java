package net.modgarden.backend.data;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

public interface Metadata {
	MapCodec<? extends Metadata> codec();

	static <T extends Metadata> MapCodec<Metadata> fromCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.flatXmap(
				DataResult::success,
				metadata -> DataResult.success((T)metadata) // We can't encode unless an unsafe cast happens.
		);
	}
}
