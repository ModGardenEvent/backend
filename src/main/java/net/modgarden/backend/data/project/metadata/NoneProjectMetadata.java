package net.modgarden.backend.data.project.metadata;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.project.ProjectMetadata;
import org.jetbrains.annotations.Nullable;

public record NoneProjectMetadata(String name) implements ProjectMetadata {
	public static final MapCodec<NoneProjectMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("name").forGetter(NoneProjectMetadata::name)
	).apply(inst, NoneProjectMetadata::new));

	@Override
	public String typeName() {
		return "none";
	}

	@Override
	public MapCodec<NoneProjectMetadata> codec() {
		return CODEC;
	}

	public record Modifiable(@Nullable String name) {
		public static final MapCodec<Modifiable> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING
						.optionalFieldOf("name")
						.forGetter(metadata -> Optional.ofNullable(metadata.name()))
		).apply(inst, (name) -> new Modifiable(name.orElse(null))));
	}
}
