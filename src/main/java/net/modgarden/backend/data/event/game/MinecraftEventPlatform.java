package net.modgarden.backend.data.event.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.event.EventPlatform;
import org.jetbrains.annotations.Nullable;

public record MinecraftEventPlatform(
			String modLoader,
			String gameVersion
) implements EventPlatform {
	public static final String ID = "minecraft";
	public static final MapCodec<MinecraftEventPlatform> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("mod_loader").forGetter(MinecraftEventPlatform::modLoader),
			Codec.STRING.fieldOf("game_version").forGetter(MinecraftEventPlatform::gameVersion)
	).apply(instance, MinecraftEventPlatform::new));

	public record Modifiable(
			@Nullable String modLoader,
			@Nullable String gameVersion
	) implements EventPlatform {
		public static final MapCodec<Modifiable> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING
						.optionalFieldOf("mod_loader")
						.forGetter(o -> Optional.ofNullable(o.modLoader())),
				Codec.STRING
						.optionalFieldOf("game_version")
						.forGetter(o -> Optional.ofNullable(o.gameVersion()))
		).apply(instance, (modLoader, gameVersion) -> new Modifiable(modLoader.orElse(null), gameVersion.orElse(null))));

		@Override
		public String game() {
			return ID;
		}

		@Override
		public MapCodec<? extends EventPlatform> getCodec() {
			return CODEC;
		}
	}

	@Override
	public String game() {
		return ID;
	}

	@Override
	public MapCodec<MinecraftEventPlatform> getCodec() {
		return CODEC;
	}
}
