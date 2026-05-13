package net.modgarden.backend.data.user.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record DiscordUserIntegration(String userId) implements Integration {
	public static final String ID = "discord";
	public static final Codec<DiscordUserIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("user_id").forGetter(DiscordUserIntegration::userId)
	).apply(inst, DiscordUserIntegration::new));

	@Override
	public Codec<DiscordUserIntegration> getCodec() {
		return CODEC;
	}
}
