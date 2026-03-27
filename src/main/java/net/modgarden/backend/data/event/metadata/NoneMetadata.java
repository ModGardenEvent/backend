package net.modgarden.backend.data.event.metadata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Metadata;

public record NoneMetadata(String name) implements Metadata {
	public static final MapCodec<NoneMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("name").forGetter(NoneMetadata::name)
	).apply(inst, NoneMetadata::new));

	@Override
	public String typeName() {
		return "none";
	}

	@Override
	public MapCodec<NoneMetadata> codec() {
		return CODEC;
	}
}
