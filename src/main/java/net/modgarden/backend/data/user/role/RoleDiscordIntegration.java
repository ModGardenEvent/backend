package net.modgarden.backend.data.user.role;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record RoleDiscordIntegration(String roleId, String permissions) implements Integration {
	public static final Codec<RoleDiscordIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("role_id").forGetter(RoleDiscordIntegration::roleId),
			Codec.STRING.fieldOf("permissions").forGetter(RoleDiscordIntegration::permissions)
	).apply(inst, RoleDiscordIntegration::new));

	@Override
	public Codec<RoleDiscordIntegration> getCodec() {
		return CODEC;
	}
}
