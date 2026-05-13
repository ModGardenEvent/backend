package net.modgarden.backend.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Optional;

/// A Codec that can be specified as 'null' when decoding/encoding.
/// Will return {@link Optional#empty()} if null.
public class NullableCodec<T> implements Codec<Optional<T>> {
	private final Codec<T> codec;

	public NullableCodec(Codec<T> codec) {
		this.codec = codec;
	}

	@Override
	public <TOps> DataResult<Pair<Optional<T>, TOps>> decode(DynamicOps<TOps> ops, TOps input) {
		DataResult<Pair<Optional<T>, TOps>> nonNullAttempt = codec.decode(ops, input)
				.map(pair -> pair.mapFirst(Optional::of));

		if (nonNullAttempt.hasResultOrPartial()) {
			return nonNullAttempt;
		}

		if (input == ops.empty()) {
			return DataResult.success(Pair.of(Optional.empty(), input));
		}

		return nonNullAttempt
				.mapError(s -> "Failed to decode nullable value. " + s);
	}

	@Override
	public <TOps> DataResult<TOps> encode(Optional<T> input, DynamicOps<TOps> ops, TOps prefix) {
		if (input.isPresent()) {
			return codec.encode(input.get(), ops, prefix);
		}
		return DataResult.success(ops.empty());
	}
}
