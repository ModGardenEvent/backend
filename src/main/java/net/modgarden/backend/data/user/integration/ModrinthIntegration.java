package net.modgarden.backend.data.user.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record ModrinthIntegration(String userId) implements Integration {
	public static final Codec<ModrinthIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("user_id").forGetter(ModrinthIntegration::userId)
	).apply(inst, ModrinthIntegration::new));

	@Override
	public Codec<ModrinthIntegration> getCodec() {
		return CODEC;
	}
}
