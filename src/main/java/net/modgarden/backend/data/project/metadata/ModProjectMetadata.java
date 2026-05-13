package net.modgarden.backend.data.project.metadata;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.project.ProjectMetadata;
import org.jetbrains.annotations.Nullable;

public record ModProjectMetadata(String modId, String name, @Nullable String description, String sourceUrl) implements ProjectMetadata {
	public static final MapCodec<ModProjectMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("mod_id").forGetter(ModProjectMetadata::modId),
			Codec.STRING.fieldOf("name").forGetter(ModProjectMetadata::name),
			Codec.STRING.optionalFieldOf("description").forGetter(ModProjectMetadata::descriptionAsOptional),
			Codec.STRING.fieldOf("source_url").forGetter(ModProjectMetadata::sourceUrl)
	).apply(inst, ModProjectMetadata::new));

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private ModProjectMetadata(String modId, String name, Optional<String> description, String sourceUrl) {
		this(modId, name, description.orElse(null), sourceUrl);
	}

	private Optional<String> descriptionAsOptional() {
		return Optional.ofNullable(description);
	}

	@Override
	public String typeName() {
		return "mod";
	}

	@Override
	public MapCodec<ModProjectMetadata> codec() {
		return CODEC;
	}
}
