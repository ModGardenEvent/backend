package net.modgarden.backend.data.user.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record ModrinthUserIntegration(String userId) implements Integration {
	public static final String ID = "modrinth";
	public static final Codec<ModrinthUserIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("user_id").forGetter(ModrinthUserIntegration::userId)
	).apply(inst, ModrinthUserIntegration::new));

	@Override
	public Codec<ModrinthUserIntegration> getCodec() {
		return CODEC;
	}
}
