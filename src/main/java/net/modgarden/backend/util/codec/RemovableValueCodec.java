package net.modgarden.backend.util.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import net.modgarden.backend.util.RemovableValue;

/// A codec that can be specified for removal if `!` is specified in front of the value,
/// or if specified as an object with the following syntax
/// ```json
/// [
/// 	"!abcde",
/// 	{
/// 		"value": "abcde",
/// 		"remove": true
/// 	}
/// ]
/// ```
/// Typically, you should make sure your key does not accept `!`. In this case, you should set allowInline to false.
///
/// @see NullableCodec NullableCodec. Should be used for maps instead of this.
public class RemovableValueCodec<T> implements Codec<RemovableValue<T>> {
	private final Codec<T> codec;
	private final boolean allowInline;

	private RemovableValueCodec(Codec<T> codec, boolean allowInline) {
		this.codec = codec;
		this.allowInline = allowInline;
	}

	/// Wraps a codec to make its value additionally specify whether it can be removed or not.
	///
	/// @param codec The codec to use within this codec.
	public static <T> RemovableValueCodec<T> removable(Codec<T> codec) {
		return removable(codec, true);
	}

	/// Wraps a codec to make its value additionally specify whether it can be removed or not.
	///
	/// @param codec The codec to use within this codec.
	/// @param allowInline Whether to allow inline `!{value}` syntax. This will only work for string values.
	public static <T> RemovableValueCodec<T> removable(Codec<T> codec, boolean allowInline) {
		return new RemovableValueCodec<>(codec, allowInline);
	}

	@Override
	public <TOps> DataResult<Pair<RemovableValue<T>, TOps>> decode(DynamicOps<TOps> ops, TOps input) {
		if (allowInline) {
			DataResult<Pair<RemovableValue<T>, TOps>> inline = handleInline(ops.getStringValue(input), ops);
			if (inline.hasResultOrPartial()) {
				return inline;
			}
		}

		DataResult<MapLike<TOps>> mapDataResult = ops.getMap(input);
		if (mapDataResult.hasResultOrPartial()) {
			MapLike<TOps> mapLike = mapDataResult.getPartialOrThrow();
			return codec.decode(ops, input)
					.map(pair -> pair.mapFirst(value -> {
						boolean remove = false;
						if (mapLike.get("remove") != null) {
							DataResult<Boolean> bool = ops.getBooleanValue(mapLike.get("remove"));
							if (bool.hasResultOrPartial()) {
								remove = bool.getPartialOrThrow();
							}
						}
						return new RemovableValue<>(value, remove);
					}));
		}

		return allowInline
				? DataResult.error(() -> "Value is not a string or a map")
				: DataResult.error(() -> "Value is not a map");
	}

	@Override
	public <TOps> DataResult<TOps> encode(RemovableValue<T> input, DynamicOps<TOps> ops, TOps prefix) {
		DataResult<TOps> encoded = codec.encode(input.value(), ops, prefix);
		if (allowInline && input.remove() && encoded.hasResultOrPartial()) {
			DataResult<String> unencoded = ops.getStringValue(encoded.getPartialOrThrow());
			if (unencoded.hasResultOrPartial()) {
				return unencoded.map(str -> ops.createString("!" + str));
			}
		}
		return encoded;
	}

	private <TOps> DataResult<Pair<RemovableValue<T>, TOps>> handleInline(DataResult<String> dataResult, DynamicOps<TOps> ops) {
		return dataResult.flatMap(str -> {
			boolean remove = str.startsWith("!");
			TOps data = remove
					? ops.createString(str.substring(1))
					: ops.createString(str);
			return codec.decode(ops, data).map(
					pair -> pair.mapFirst(value -> new RemovableValue<>(value, remove))
			);
		});
	}
}
