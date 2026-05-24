package net.modgarden.backend.data.user.role;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.Integration;

public record DiscordUserRoleIntegration(String roleId) implements Integration {
	public static final String ID = "discord";
	public static final Codec<DiscordUserRoleIntegration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("role_id").forGetter(DiscordUserRoleIntegration::roleId)
	).apply(inst, DiscordUserRoleIntegration::new));

	@Override
	public Codec<DiscordUserRoleIntegration> getCodec() {
		return CODEC;
	}
}
