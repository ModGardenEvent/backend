package net.modgarden.backend.data.user.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record DiscordIntegration(String userId) implements Integration {
	public static final Codec<DiscordIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("user_id").forGetter(DiscordIntegration::userId)
	).apply(inst, DiscordIntegration::new));

	@Override
	public Codec<DiscordIntegration> getCodec() {
		return CODEC;
	}
}
