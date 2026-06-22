package net.modgarden.backend.data.user.integration;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record MinecraftUserIntegration(List<String> accounts) implements Integration {
	public static final String ID = "minecraft";
	public static final Codec<MinecraftUserIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.list(Codec.STRING).fieldOf("accounts").forGetter(MinecraftUserIntegration::accounts)
	).apply(inst, MinecraftUserIntegration::new));

	@Override
	public Codec<MinecraftUserIntegration> getCodec() {
		return CODEC;
	}
}
