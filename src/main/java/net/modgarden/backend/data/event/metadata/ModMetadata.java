package net.modgarden.backend.data.event.metadata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Metadata;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ModMetadata(String modId, String name, @Nullable String description, String sourceUrl) implements Metadata {
	public static final MapCodec<ModMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("mod_id").forGetter(ModMetadata::modId),
			Codec.STRING.fieldOf("name").forGetter(ModMetadata::name),
			Codec.STRING.optionalFieldOf("description").forGetter(ModMetadata::descriptionAsOptional),
			Codec.STRING.fieldOf("source_url").forGetter(ModMetadata::sourceUrl)
	).apply(inst, ModMetadata::new));

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private ModMetadata(String modId, String name, Optional<String> description, String sourceUrl) {
		this(modId, name, description.orElse(null), sourceUrl);
	}

	private Optional<String> descriptionAsOptional() {
		return Optional.ofNullable(description);
	}

	@Override
	public String getName() {
		return "mod";
	}

	@Override
	public MapCodec<ModMetadata> codec() {
		return CODEC;
	}
}
