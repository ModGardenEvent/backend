package net.modgarden.backend.util;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.FieldDecoder;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// TODO: Document this class. - Calico.
/// This accounts for a DFU bug where RecordCodecBuilder swaps the half-point at which members are encoded, as well as
/// moving any encoded {@link KeyDispatchCodec} based fields to the top of the encoded map, which is a change that Mojang
/// will not make because it'd mess heavily with {@link DSL#remainder()} based data fixing.
///
/// This should only ever modify map encoding, and the encoding of lists that contain maps.
///
/// The code below is not for children or those who are easily disturbed.
///
/// @see <a href="https://github.com/Mojang/DataFixerUpper/issues/101">Mojang/DataFixerUpper#101</a>
/// @param <E> The type parameter of the root codec.
public class ReadableOrderCodec<E> implements Codec<E> {
	private final Codec<E> codec;

	public ReadableOrderCodec(Codec<E> codec) {
		this.codec = codec;
	}

	@Override
	public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
		return codec.decode(ops, input);
	}

	@Override
	public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
		return codec.encode(input, ops, prefix).map(value -> {
			FieldLocationSets fieldLocations = new FieldLocationSets(new HashSet<>(), new HashSet<>());
			addToKeyDispatchFieldLocationSet(fieldLocations, ops, value, codec, null);
			return tryToCorrectElement(fieldLocations, ops, value, null);
		});
	}
	private <T> T tryToCorrectElement(FieldLocationSets fieldLocations,
									  DynamicOps<T> ops, T element,
									  @Nullable FieldLocation fieldLocation) {
		var listResult = ops.getStream(element).resultOrPartial();
		if (listResult.isPresent()) {
			var list = new ArrayList<>(listResult.get().toList());
			for (int i = 0; i < list.size(); ++i) {
				list.set(i, tryToCorrectElement(
						fieldLocations, ops, list.get(i),
						new FieldLocation(fieldLocation, i))
				);
			}
			return ops.createList(list.stream());
		}
		var mapResult = ops.getMap(element).resultOrPartial();
		if (mapResult.isPresent()) {
			return correctEncoding(fieldLocations, ops, ops.mapBuilder(), mapResult.get(), fieldLocation)
					.build(ops.empty())
					.resultOrPartial()
					.orElse(element);
		}
		return element;
	}

	private <T> RecordBuilder<T> correctEncoding(FieldLocationSets fieldLocations, DynamicOps<T> ops, RecordBuilder<T> builder,
												 MapLike<T> newValues, @Nullable FieldLocation fieldLocation) {
		List<Pair<T, T>> elements = newValues.entries()
				.collect(Collectors.toCollection(ArrayList::new));

		for (var element : newValues.entries().toList()) {
			String key = ops.getStringValue(element.getFirst())
					.resultOrPartial()
					.orElseThrow();
			FieldLocation mappedFieldLocation = new FieldLocation(fieldLocation, key);
			if (fieldLocations.keyDispatchFields.contains(mappedFieldLocation)) {
				fieldLocations.keyDispatchFields.remove(mappedFieldLocation);
				elements.remove(element);
				builder.add(element.getFirst(), element.getSecond());
			}
		}

		if (elements.size() > 4) {
			if (fieldLocations.recordCodecBuilderFields.contains(fieldLocation)) {
				orderRecord(builder, elements, fieldLocations, ops, fieldLocation);
			}
			return builder;
		}

		for (Pair<T, T> entry : elements) {
			T key = entry.getFirst();
			T value = tryToCorrectElement(
					fieldLocations,
					ops,
					entry.getSecond(),
					new FieldLocation(fieldLocation, ops.getStringValue(key)
							.resultOrPartial().orElseThrow())
			);
			builder.add(key, value);
		}

		return builder;
	}

	private <T> void orderRecord(RecordBuilder<T> builder, List<Pair<T, T>> elements,
								 FieldLocationSets fieldLocations, DynamicOps<T> ops, FieldLocation fieldLocation) {
		if (elements.size() > 16) {
			throw new UnsupportedOperationException("Unable to order RecordCodecBuilders with more than 16 values.");
		}
		Map<T, T> orderedElements = new LinkedHashMap<>();
		if (elements.size() > 4 && elements.size() < 9) {
			int divisor = (int) Math.ceil(elements.size() / 2.0);
			for (int i = divisor; i < elements.size(); ++i) {
				insertValueInOrderedMap(elements.get(i), orderedElements, fieldLocations, ops, fieldLocation);
			}
			for (int i = 0; i < divisor; ++i) {
				insertValueInOrderedMap(elements.get(i), orderedElements, fieldLocations, ops, fieldLocation);
			}
		} else if (elements.size() > 9) {
			int divisor = (int) Math.ceil(elements.size() / 2.0);
			List<Pair<T, T>> firstHalf = elements.subList(divisor, elements.size());
			List<Pair<T, T>> secondHalf = elements.subList(0, divisor);
			orderRecord(builder, firstHalf, fieldLocations, ops, fieldLocation);
			orderRecord(builder, secondHalf, fieldLocations, ops, fieldLocation);
		} else if (elements.size() == 9) { // Hardcode 9 here, it's a bit pesky and kinda plays by its own rules.
			for (int i = 5; i < 9; ++i) {
				insertValueInOrderedMap(elements.get(i), orderedElements, fieldLocations, ops, fieldLocation);
			}
			for (int i = 3; i < 5; ++i) {
				insertValueInOrderedMap(elements.get(i), orderedElements, fieldLocations, ops, fieldLocation);
			}
			for (int i = 0; i < 3; ++i) {
				insertValueInOrderedMap(elements.get(i), orderedElements, fieldLocations, ops, fieldLocation);
			}
		} else {
			for (Pair<T, T> element : elements) {
				insertValueInOrderedMap(element, orderedElements, fieldLocations, ops, fieldLocation);
			}
		}
		orderedElements.forEach(builder::add);
	}

	private <T> void insertValueInOrderedMap(Pair<T, T> element,
											 Map<T, T> orderedElements,
											 FieldLocationSets fieldLocations,
											 DynamicOps<T> ops,
											 FieldLocation fieldLocation) {
		T key = element.getFirst();
		T value = tryToCorrectElement(fieldLocations, ops, element.getSecond(), ops.getStringValue(key)
				.resultOrPartial()
				.map(s -> new FieldLocation(fieldLocation, s))
				.orElseThrow());
		orderedElements.put(key, value);
	}

	private <T> void addToKeyDispatchFieldLocationSet(FieldLocationSets locations, DynamicOps<T> ops, T rootValue,
													  Codec<?> codec, @Nullable FieldLocation fieldLocation) {
		Codec<?> finalCodec = mapAwayFromRecursiveCodec(codec);
		if (finalCodec instanceof MapCodec.MapCodecCodec<?>(MapCodec<?> mapCodec)) {
			if (mapCodec instanceof KeyDispatchCodec<?, ?> keyDispatchCodec) {
				addKeyDispatchFieldLocationToSet(locations, ops, rootValue, fieldLocation, keyDispatchCodec);
				return;
			}
			@Nullable RecordCodecBuilder<?, ?> recordCodecBuilder = reflectInternalBuilderFromRecordCodec(mapCodec);
			if (recordCodecBuilder != null) {
				MapDecoder<?> rootMapDecoder = reflectDecoderFromRecordCodecBuilder(recordCodecBuilder);
				addToKeyDispatchFieldLocationSet(locations, ops, rootValue, rootMapDecoder, fieldLocation);
			}
			return;
		}
		if (finalCodec instanceof ListCodec<?> listCodec) {
			T fieldValue = fieldLocation == null ? rootValue : fieldLocation.getEncasedField(ops, rootValue);
			int fieldCount = ops.getStream(fieldValue).getOrThrow().toArray().length;
			for (int i = 0; i < fieldCount; ++i) {
				addToKeyDispatchFieldLocationSet(locations, ops, rootValue, listCodec.elementCodec(), new FieldLocation(fieldLocation, i));
			}
		}
		// We don't really have to worry about CompoundListCodec because I doubt anybody is going to actually use it. It feels more like Legacy Minecraft code imo.
	}

	private <T> void addToKeyDispatchFieldLocationSet(FieldLocationSets locations, DynamicOps<T> ops, T rootValue,
													  MapDecoder<?> rootDecoder, @Nullable FieldLocation fieldLocation) {
		List<MapDecoder<?>> reflectedFieldsFromRecordCodecBuilder = reflectDecodersFromRecordCodecDecoder(rootDecoder, locations.recordCodecBuilderFields, fieldLocation);
		if (reflectedFieldsFromRecordCodecBuilder.isEmpty()) {
			locations.recordCodecBuilderFields.add(fieldLocation);
		}
		for (MapDecoder<?> decoder : reflectedFieldsFromRecordCodecBuilder) {
			LinkedHashSet<String> keys = decoder.keys(JsonOps.INSTANCE)
					.map(jsonElement -> jsonElement.getAsJsonPrimitive().getAsString())
					.collect(Collectors.toCollection(LinkedHashSet::new));

			if (keys.isEmpty()) continue;

			FieldLocation newFieldLocation = new FieldLocation(fieldLocation, keys.getFirst());

			if (!(decoder instanceof MapCodec<?> mapCodec)) {
				List<MapDecoder<?>> reflectedCodecs = reflectDecodersFromRecordCodecDecoder(decoder, locations.recordCodecBuilderFields, fieldLocation);
				if (reflectedCodecs.isEmpty()) {
					locations.recordCodecBuilderFields.add(fieldLocation);
				}
				for (MapDecoder<?> innerDecoder : reflectedCodecs) {
					addToKeyDispatchFieldLocationSet(locations, ops, rootValue, innerDecoder, fieldLocation);
				}
				continue;
			}

			// Check whether the internal MapCodec is a RecordCodecBuilder.
			@Nullable RecordCodecBuilder<?, ?> recordCodecBuilder = reflectInternalBuilderFromRecordCodec(mapCodec);
			if (recordCodecBuilder != null) {
				MapDecoder<?> rootMapDecoder = reflectDecoderFromRecordCodecBuilder(recordCodecBuilder);
				addToKeyDispatchFieldLocationSet(locations, ops, rootValue, rootMapDecoder, newFieldLocation);
			}

			MapDecoder<?> internalDecoder = reflectElementDecoderFromFieldMapCodec(mapCodec);
			if (internalDecoder instanceof FieldDecoder<?> fieldDecoder) {
				@Nullable Codec<?> elementCodec = mapAwayFromRecursiveCodec(reflectCodecFromFieldDecoder(fieldDecoder));
				if (elementCodec == null) continue;
				addToKeyDispatchFieldLocationSet(locations, ops, rootValue, elementCodec, newFieldLocation);
			}
		}
	}

	private <T> void addKeyDispatchFieldLocationToSet(FieldLocationSets locations, DynamicOps<T> ops, T rootValue, FieldLocation currentLocation, KeyDispatchCodec<?, ?> keyDispatchCodec) {
		String fieldKey = keyDispatchCodec.keys(ops)
				.map(t -> ops.getStringValue(t).resultOrPartial().orElseThrow())
				.toList()
				.getFirst();
		FieldLocation dispatchTypeFieldLocation = new FieldLocation(currentLocation, fieldKey);
		locations.keyDispatchFields.add(dispatchTypeFieldLocation); // We have a match!

		MapDecoder<?> dispatchValueDecoder = reflectDecoderFromKeyDispatchCodec(ops, dispatchTypeFieldLocation.getEncasedField(ops, rootValue), keyDispatchCodec);
		List<MapDecoder<?>> dispatchDecoders = reflectDecodersFromRecordCodecDecoder(dispatchValueDecoder, locations.recordCodecBuilderFields, currentLocation);
		for (MapDecoder<?> dispatchDecoder : dispatchDecoders) {
			addToKeyDispatchFieldLocationSet(locations, ops, rootValue, dispatchDecoder, currentLocation);
		}
	}

	/// Represents a location within the encoded values.
	private record FieldLocation(@Nullable FieldLocation previousValue,
								 @Nullable String key, int listIndex) {
		private FieldLocation {
			if (key == null && listIndex == -1)
				throw new IllegalStateException("Can't create a field location without a key or a list index.");
		}

		private FieldLocation(@Nullable FieldLocation previousValue, String key) {
			this(previousValue, key, -1);
		}

		private FieldLocation(@Nullable FieldLocation previousValue, int listIndex) {
			this(previousValue, null, listIndex);
		}

		public <T> T getEncasedField(DynamicOps<T> ops, T rootValue) {
			List<FieldLocation> valueList = new ArrayList<>();
			FieldLocation addValue = this;
			while (addValue != null) {
				valueList.add(addValue);
				addValue = addValue.previousValue;
			}
			Collections.reverse(valueList);

			T returnValue = rootValue;
			for (FieldLocation operatingValue : valueList) {
				if (operatingValue.key() != null) {
					returnValue = ops.getMap(returnValue)
							.resultOrPartial()
							.orElseThrow()
							.get(operatingValue.key());
				} else if (operatingValue.listIndex() > -1) {
					returnValue = ops.getStream(returnValue)
							.resultOrPartial()
							.orElseThrow()
							.toList()
							.get(operatingValue.listIndex());
				} else {
					throw new UnsupportedOperationException("Could not get specific value within map or list.");
				}
			}
			return returnValue;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof FieldLocation(ReadableOrderCodec.FieldLocation otherPreviousValue, String otherKey, int otherListIndex))) {
				return false;
			}
			return (previousValue == null && otherPreviousValue == null || previousValue != null && previousValue.equals(otherPreviousValue)) &&
					(key == null && otherKey == null || key != null && key.equals(otherKey)) &&
					listIndex == otherListIndex;
		}

		@Override
		public int hashCode() {
			return Objects.hash(previousValue, key, listIndex);
		}

		@NotNull
		@Override
		public String toString() {
			if (previousValue == null) {
				if (key != null) {
					return key;
				}
				return "[" + listIndex + "]";
			}
			return previousValue + (key != null ? "." + key : "") +
					(listIndex != -1 ? "[" + listIndex + "]" : "");
		}
	}

	private record FieldLocationSets(Set<FieldLocation> keyDispatchFields, Set<FieldLocation> recordCodecBuilderFields) {}

	private static Codec<?> mapAwayFromRecursiveCodec(Codec<?> codec) {
		return codec instanceof Codec.RecursiveCodec<?> recursiveCodec ?
				reflectInternalCodecFromRecursiveCodec(recursiveCodec) : codec;
	}

	private static Codec<?> reflectInternalCodecFromRecursiveCodec(RecursiveCodec<?> recursiveCodec) {
		try {
			Field f = recursiveCodec.getClass().getDeclaredField("wrapped");
			f.setAccessible(true);
			if (f.get(recursiveCodec) instanceof Supplier<?> supplier && supplier.get() instanceof Codec<?> codec) {
				return codec;
			}
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		throw new UnsupportedOperationException("Could not obtain 'wrapped' field within RecursiveCodec.");
	}

	/// Potentially gets a RecordCodecBuilder from a map codec.
	///
	/// @param mapCodec A MapCodec.
	/// @return The internal RecordCodecBuilder, or null if the MapCodec is not a RecordCodecBuilder based codec.
	@Nullable
	private static <E> RecordCodecBuilder<?, ?> reflectInternalBuilderFromRecordCodec(MapCodec<E> mapCodec) {
		try {
			Field f = mapCodec.getClass().getDeclaredField("val$builder");
			f.setAccessible(true);
			return (RecordCodecBuilder<?, ?>) f.get(mapCodec);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		return null;
	}

	private static MapDecoder<?> reflectDecoderFromRecordCodecBuilder(RecordCodecBuilder<?, ?> recordCodecBuilder) {
		try {
			Field f = recordCodecBuilder.getClass().getDeclaredField("decoder");
			f.setAccessible(true);
			return (MapDecoder<?>) f.get(recordCodecBuilder);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		throw new UnsupportedOperationException("Could not obtain 'decoder' field within RecordCodecBuilder.");
	}

	private static MapDecoder<?> reflectElementDecoderFromFieldMapCodec(MapCodec<?> mapCodec) {
		try {
			Field recordCodecBuilder = mapCodec.getClass().getDeclaredField("val$decoder");
			recordCodecBuilder.setAccessible(true);
			return (MapDecoder<?>) recordCodecBuilder.get(mapCodec);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		return null;
	}


	private static Codec<?> reflectCodecFromFieldDecoder(FieldDecoder<?> mapCodec) {
		try {
			Field recordCodecBuilder = mapCodec.getClass().getDeclaredField("elementCodec");
			recordCodecBuilder.setAccessible(true);
			return (Codec<?>) recordCodecBuilder.get(mapCodec);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		return null;
	}

	/// Reflects all codecs from a {@link RecordCodecBuilder}.
	///
	/// @param decoder The decoder to retrieve decoders from.
	/// @return A list of MapDecoders obtained from the decoder.
	private static List<MapDecoder<?>> reflectDecodersFromRecordCodecDecoder(MapDecoder<?> decoder, Set<FieldLocation> recordCodecBuilderFields, FieldLocation currentLocation) {
		List<MapDecoder<?>> decoders = new ArrayList<>();
		try {
			for (Field field : decoder.getClass().getDeclaredFields()) {
				field.setAccessible(true);
				Object object = field.get(decoder);
				if (object instanceof MapDecoder<?> innerDecoder) {
					decoders.addAll(reflectDecodersFromRecordCodecDecoder(innerDecoder, recordCodecBuilderFields, currentLocation));
				} else if (object instanceof RecordCodecBuilder<?, ?> recordCodecBuilder) {
					MapDecoder<?> innerDecoder = reflectDecoderFromRecordCodecBuilder(recordCodecBuilder);
					if (field.getName().startsWith("val$function")) {
						decoders.addAll(reflectDecodersFromRecordCodecDecoder(innerDecoder, recordCodecBuilderFields, currentLocation));
					} else {
						decoders.add(innerDecoder);
					}
					recordCodecBuilderFields.add(currentLocation);
				}
			}
		} catch (IllegalAccessException ignored) {}
		return decoders;
	}

	@SuppressWarnings("unchecked")
	private static <T, K, V> MapDecoder<? extends V> reflectDecoderFromKeyDispatchCodec(DynamicOps<T> value, T keyValue, KeyDispatchCodec<K, V> dispatchCodec) {
		try {
			Field keyCodecField = dispatchCodec.getClass().getDeclaredField("keyCodec");
			keyCodecField.setAccessible(true);
			Object keyCodecAsObj = keyCodecField.get(dispatchCodec);
			if (keyCodecAsObj instanceof Codec<?>) {
				Codec<K> keyCodec = (Codec<K>) keyCodecAsObj;
				DataResult<K> decodedKey = keyCodec.parse(value, keyValue);
				if (!decodedKey.hasResultOrPartial())
					throw new Exception();
				K result = decodedKey.resultOrPartial().orElseThrow();

				Field decoderField = dispatchCodec.getClass().getDeclaredField("decoder");
				decoderField.setAccessible(true);
				var decoder = (Function<? super K, ? extends DataResult<? extends MapDecoder<? extends V>>>) decoderField.get(dispatchCodec);
				return decoder.apply(result).resultOrPartial().orElseThrow();
			}
		} catch (Exception ignored) {}
		throw new UnsupportedOperationException("Could not obtain either 'keyCodec' or 'decoder' field within KeyDispatchCodec.");
	}
}
