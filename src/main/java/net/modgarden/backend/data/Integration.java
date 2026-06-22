package net.modgarden.backend.data;

import com.mojang.serialization.Codec;

public interface Integration {
	Codec<?> getCodec();

	@SuppressWarnings("unchecked")
	static <T extends Integration> Codec<Integration> fromCodec(Codec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				integration -> (T)integration // We can't encode unless an unsafe cast happens.
		);
	}
}
