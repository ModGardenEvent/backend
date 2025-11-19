package net.modgarden.backend.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtObjectCodec implements Codec<Object> {
	protected ExtObjectCodec() {}

	@Override
	public <T> DataResult<Pair<Object, T>> decode(DynamicOps<T> ops, T input) {
		Object result = decodeResult(ops, input);
		if (result != null) {
			return DataResult.success(Pair.of(result, input));
		}
		return DataResult.error(() -> "Failed to find a compatible data type to decode input " + input + " with.");
	}

	@Override
	public <T> DataResult<T> encode(Object input, DynamicOps<T> ops, T prefix) {
		T result = encodeResult(ops, input);
		if (result != null) {
			return DataResult.success(result);
		}
		return DataResult.error(() -> "Failed to find a compatible data type to encode input " + input + " with.");
	}

	private static <T> Object decodeResult(DynamicOps<T> ops, T input) {
		DataResult<Number> numberResult = ops.getNumberValue(input);
		if (numberResult.isSuccess()) {
			return numberResult.getOrThrow();
		}
		DataResult<String> stringResult = ops.getStringValue(input);
		if (stringResult.isSuccess()) {
			return stringResult.getOrThrow();
		}
		DataResult<Stream<T>> listResult = ops.getStream(input);
		if (listResult.isSuccess()) {
			return listResult.getOrThrow()
					.map(t -> decodeResult(ops, t))
					.toList();
		}
		DataResult<MapLike<T>> mapResult = ops.getMap(input);
		if (mapResult.isSuccess()) {
			return mapResult.getOrThrow()
					.entries()
					.map(t -> Pair.of(decodeResult(ops, t.getFirst()), decodeResult(ops, t.getSecond())))
					.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		}
		return null;
	}

	private static <T> T encodeResult(DynamicOps<T> ops, Object input) {
		if (input instanceof Number number) {
			return ops.createNumeric(number);
		}
		if (input instanceof String string) {
			return ops.createString(string);
		}
		if (input instanceof List<?> list) {
			return ops.createList(list.stream()
					.map(o -> encodeResult(ops, o)));
		}
		if (input instanceof Map<?, ?> map) {
			return ops.createMap(map.entrySet().stream()
					.map(entry -> Pair.of(encodeResult(ops, entry.getKey()), encodeResult(ops, entry.getValue()))));
		}
		return null;
	}
}
