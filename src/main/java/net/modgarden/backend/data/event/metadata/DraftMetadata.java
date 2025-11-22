package net.modgarden.backend.data.event.metadata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Metadata;

public record DraftMetadata(String name) implements Metadata {
	public static final MapCodec<DraftMetadata> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("name").forGetter(DraftMetadata::name)
	).apply(inst, DraftMetadata::new));

	@Override
	public MapCodec<DraftMetadata> codec() {
		return CODEC;
	}
}
