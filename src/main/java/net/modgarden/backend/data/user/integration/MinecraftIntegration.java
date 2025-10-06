package net.modgarden.backend.data.user.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

import java.util.List;

public record MinecraftIntegration(List<String> accounts) implements Integration {
	public static final Codec<MinecraftIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.list(Codec.STRING).fieldOf("accounts").forGetter(MinecraftIntegration::accounts)
	).apply(inst, MinecraftIntegration::new));

	@Override
	public Codec<MinecraftIntegration> getCodec() {
		return CODEC;
	}
}
