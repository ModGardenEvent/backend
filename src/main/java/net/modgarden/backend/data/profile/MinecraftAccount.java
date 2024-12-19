package net.modgarden.backend.data.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public record MinecraftAccount(UUID uuid,
                               boolean verified,
                               Optional<GlobalMinecraftAccount> asGlobal) {
    public static final Codec<MinecraftAccount> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(MinecraftAccount::uuid),
            Codec.BOOL.fieldOf("verified").forGetter(MinecraftAccount::verified)
    ).apply(inst, MinecraftAccount::new));

    public MinecraftAccount(UUID uuid, boolean verified) {
        this(uuid, verified, Optional.empty());
    }

    public MinecraftAccount(GlobalMinecraftAccount globalMinecraftAccount, String userId) {
        this(globalMinecraftAccount.uuid(), globalMinecraftAccount.verifiedTo().isPresent() && globalMinecraftAccount.verifiedTo().get().equals(userId), Optional.of(globalMinecraftAccount));
    }
}
