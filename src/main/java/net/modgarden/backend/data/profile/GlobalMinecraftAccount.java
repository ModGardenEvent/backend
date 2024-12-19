package net.modgarden.backend.data.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public record GlobalMinecraftAccount(UUID uuid,
                                     Optional<String> verifiedTo) {
    public static final Codec<GlobalMinecraftAccount> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(GlobalMinecraftAccount::uuid),
            Codec.STRING.optionalFieldOf("verified_to").forGetter(GlobalMinecraftAccount::verifiedTo)
    ).apply(inst, GlobalMinecraftAccount::new));
}
