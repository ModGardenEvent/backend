package net.modgarden.backend.data.event.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.event.EventPlatform;

public record MinecraftEventPlatform(
			String modLoader,
			String gameVersion
) implements EventPlatform {
	public static final String ID = "minecraft";
	public static final MapCodec<MinecraftEventPlatform> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("mod_loader").forGetter(MinecraftEventPlatform::modLoader),
			Codec.STRING.fieldOf("game_version").forGetter(MinecraftEventPlatform::gameVersion)
	).apply(instance, MinecraftEventPlatform::new));

	@Override
	public String game() {
		return ID;
	}

	@Override
	public MapCodec<MinecraftEventPlatform> getCodec() {
		return CODEC;
	}
}
