package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;

public enum AwardTier {
	COMMON,
	UNCOMMON,
	RARE,
	LEGENDARY;
	public static final Codec<AwardTier> CODEC = Codec.STRING.xmap(AwardTier::valueOf, AwardTier::name);
}
