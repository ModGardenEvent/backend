package net.modgarden.backend.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/// Accounts for a DFU bug where RecordCodecBuilder swaps the half-point at which members are encoded.
///
/// This should only ever modify map encoding, which is where this bug is present.
///
/// @see <a href="https://github.com/Mojang/DataFixerUpper/issues/101">Mojang/DataFixerUpper#101</a>
/// @param <E> The type parameter of the RecordCodecBuilder.
@SuppressWarnings("ClassCanBeRecord")
public class OrderCorrectedCodec<E> implements Codec<E> {
	private final Codec<E> codec;

	public OrderCorrectedCodec(Codec<E> codec) {
		this.codec = codec;
	}

	@Override
	public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
		return codec.decode(ops, input);
	}

	@Override
	public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
		return codec.encode(input, ops, prefix).map(value -> {
			DataResult<MapLike<T>> mapLike = ops.getMap(value);
			if (!mapLike.hasResultOrPartial()) {
				return value;
			}
			return correctEncoding(
					ops,
					ops.mapBuilder(),
					mapLike.getOrThrow()
			).build(ops.empty())
					.resultOrPartial()
					.orElse(value);
		});
	}

	private static <T> RecordBuilder<T> correctEncoding(DynamicOps<T> ops, RecordBuilder<T> builder, MapLike<T> newValues) {
		List<Pair<T, T>> elements = newValues.entries()
				.collect(Collectors.toCollection(ArrayList::new));
		// TODO: Un-hardcode from 'type' and try to detect where dispatch codecs are. This will work for now.
		Optional<Pair<T, T>> dispatchKey = elements.stream().filter(pair -> ops.getStringValue(pair.getFirst())
				.resultOrPartial()
				.orElse("null")
				.equals("type")
		).findAny();
		dispatchKey.ifPresent(pair -> {
			builder.add(pair.getFirst(), pair.getSecond());
			elements.remove(pair);
		});

		if (elements.size() > 3) {
			for (int secondHalfIndex = (int)Math.ceil(elements.size() / 2.0F); secondHalfIndex < elements.size(); ++secondHalfIndex) {
				T key = elements.get(secondHalfIndex).getFirst();
				T value = potentiallyCorrectElement(ops, elements.get(secondHalfIndex).getSecond());
				builder.add(key, value);
			}
			for (int firstHalfIndex = 0; firstHalfIndex < Math.ceil(elements.size() / 2.0F); ++firstHalfIndex) {
				T key = elements.get(firstHalfIndex).getFirst();
				T value = potentiallyCorrectElement(ops, elements.get(firstHalfIndex).getSecond());
				builder.add(key, value);
			}
		} else {
			for (Pair<T, T> entry : newValues.entries().toList()) {
				T key = entry.getFirst();
				T value = potentiallyCorrectElement(ops, entry.getSecond());
				builder.add(key, value);
			}
		}

		return builder;
	}

	private static <T> T potentiallyCorrectElement(DynamicOps<T> ops, T element) {
		var mapResult = ops.getMap(element).resultOrPartial();
		if (mapResult.isPresent()) {
			return correctEncoding(ops, ops.mapBuilder(), mapResult.get()).build(ops.empty())
					.resultOrPartial()
					.orElse(element);
		}
		return element;
	}
}
