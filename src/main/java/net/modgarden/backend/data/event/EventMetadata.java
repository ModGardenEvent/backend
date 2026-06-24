package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.codec.NullableCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record EventMetadata(
		String name,
		@Nullable String description
) {
	public static final Codec<EventMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING
					.fieldOf("name")
					.forGetter(EventMetadata::name),
			Codec.STRING
					.optionalFieldOf("description")
					.forGetter(md -> Optional.ofNullable(md.description))
	).apply(instance, (name, description) ->
			new EventMetadata(name, description.orElse(null))));

	public record Modifiable(
			@Nullable String name,
			@Nullable NullableWrapper<String> description
	) {
		public static final Codec<EventMetadata.Modifiable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING
						.optionalFieldOf("name")
						.forGetter(md -> Optional.ofNullable(md.name())),
				NullableCodec.nullable(Codec.STRING)
						.optionalFieldOf("description")
						.forGetter(md -> Optional.ofNullable(md.description()))
		).apply(instance, (name, description) ->
				new EventMetadata.Modifiable(name.orElse(null), description.orElse(null))));
	}
}
