package net.modgarden.backend.util.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.modgarden.backend.util.NullableWrapper;

/// A Codec that can be specified as 'null' when decoding/encoding.
/// Will return {@link NullableWrapper#empty()} if null.
///
/// @see RemovableValueCodec RemovableValueCodec. Should be used for list values instead of this.
public class NullableCodec<T> implements Codec<NullableWrapper<T>> {
	private final Codec<T> codec;

	private NullableCodec(Codec<T> codec) {
		this.codec = codec;
	}

	public static <T> NullableCodec<T> nullable(Codec<T> codec) {
		return new NullableCodec<>(codec);
	}

	@Override
	public <TOps> DataResult<Pair<NullableWrapper<T>, TOps>> decode(DynamicOps<TOps> ops, TOps input) {
		DataResult<Pair<NullableWrapper<T>, TOps>> nonNullAttempt = codec.decode(ops, input)
				.map(pair -> pair.mapFirst(NullableWrapper::of));

		if (nonNullAttempt.hasResultOrPartial()) {
			return nonNullAttempt;
		}

		if (input == ops.empty()) {
			return DataResult.success(Pair.of(NullableWrapper.empty(), input));
		}

		return nonNullAttempt
				.mapError(s -> "Failed to decode nullable value. " + s);
	}

	@Override
	public <TOps> DataResult<TOps> encode(NullableWrapper<T> input, DynamicOps<TOps> ops, TOps prefix) {
		if (input.isPresent()) {
			return codec.encode(input.value(), ops, prefix);
		}
		return DataResult.success(ops.empty());
	}
}
