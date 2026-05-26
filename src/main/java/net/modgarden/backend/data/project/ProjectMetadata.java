package net.modgarden.backend.data.project;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

public interface ProjectMetadata {
	String typeName();
	MapCodec<? extends ProjectMetadata> codec();

	static <T extends ProjectMetadata> MapCodec<ProjectMetadata> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}

	record Modifiable(@Nullable String typeName) {
		public static final MapCodec<Modifiable> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING
						.optionalFieldOf("type_name")
						.forGetter(metadata -> Optional.ofNullable(metadata.typeName()))
		).apply(inst, (typeName) -> new Modifiable(typeName.orElse(null))));
	}
}
