package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public interface Integration {
	Codec<?> getCodec();

	static <T extends Integration> Codec<Integration> fromCodec(Codec<T> codec) {
		return codec.flatComapMap(
				t -> t,
				_ -> DataResult.error(() -> "Cannot safely convert from a typed integration to a generic integration.")
		);
	}
}
