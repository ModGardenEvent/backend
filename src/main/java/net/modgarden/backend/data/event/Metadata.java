package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Metadata(
		String name,
		String description
) {
	public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(Metadata::name),
			Codec.STRING.fieldOf("description").forGetter(Metadata::description)
	).apply(instance, Metadata::new));
}
