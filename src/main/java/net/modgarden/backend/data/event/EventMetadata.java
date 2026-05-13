package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record EventMetadata(
		String name,
		@Nullable String description
) {
	public static final Codec<EventMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(EventMetadata::name),
			Codec.STRING.optionalFieldOf("description").forGetter(md -> Optional.ofNullable(md.description))
	).apply(instance, (name, description) -> new EventMetadata(name, description.orElse(null))));
}
