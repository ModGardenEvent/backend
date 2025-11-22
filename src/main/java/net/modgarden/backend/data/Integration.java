package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public interface Integration {
	Codec<?> getCodec();

	@SuppressWarnings("unchecked")
	static <T extends Integration> Codec<Integration> fromCodec(Codec<T> codec) {
		return codec.flatComapMap(
				t -> t,
				integration -> DataResult.success((T)integration) // We can't encode unless an unsafe cast happens.
		);
	}
}
