package net.modgarden.backend.util;

import com.mojang.serialization.Codec;

import java.math.BigInteger;
import java.util.UUID;

public class ExtraCodecs {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(string -> new UUID(
            new BigInteger(string.substring(0, 16), 16).longValue(),
            new BigInteger(string.substring(16), 16).longValue()
    ), uuid -> uuid.toString().replace("-", ""));
}
