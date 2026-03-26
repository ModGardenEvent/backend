package net.modgarden.backend.data.user.role;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record DiscordIntegration(String roleId, String permissions) implements Integration {
	public static final Codec<DiscordIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("role_id").forGetter(DiscordIntegration::roleId),
			Codec.STRING.fieldOf("permissions").forGetter(DiscordIntegration::permissions)
	).apply(inst, DiscordIntegration::new));

	@Override
	public Codec<DiscordIntegration> getCodec() {
		return CODEC;
	}
}
