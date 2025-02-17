package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.profile.User;

public record AwardInstance(String awardId,
                            String awardedTo,
                            String customData,
							String submissionId,
							AwardTier tier) {
    public static final Codec<AwardInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Award.ID_CODEC.fieldOf("award_id").forGetter(AwardInstance::awardId),
            User.ID_CODEC.fieldOf("awarded_to").forGetter(AwardInstance::awardedTo),
            Codec.STRING.fieldOf("custom_data").forGetter(AwardInstance::customData),
			Codec.STRING.fieldOf("submission_id").forGetter(AwardInstance::submissionId),
			AwardTier.CODEC.fieldOf("tier_override").forGetter(AwardInstance::tier)
    ).apply(inst, AwardInstance::new));

    public record UserValues(String awardId,
                             String customData) {
        public static final Codec<UserValues> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Award.ID_CODEC.fieldOf("award_id").forGetter(UserValues::awardId),
                Codec.STRING.fieldOf("custom_data").forGetter(UserValues::customData)
        ).apply(inst, UserValues::new));
    }

	public record FullAwardData(String awardId,
								String awardedTo,
								String customData,
								String slug,
								String displayName,
								String sprite,
								String discordEmote,
								String tooltip,
								String submissionId,
								AwardTier tier) {
		public static final Codec<FullAwardData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Award.ID_CODEC.fieldOf("award_id").forGetter(FullAwardData::awardId),
				User.ID_CODEC.fieldOf("awarded_to").forGetter(FullAwardData::awardedTo),
				Codec.STRING.fieldOf("custom_data").forGetter(FullAwardData::customData),
				Codec.STRING.fieldOf("slug").forGetter(FullAwardData::slug),
				Codec.STRING.fieldOf("display_name").forGetter(FullAwardData::displayName),
				Codec.STRING.fieldOf("sprite").forGetter(FullAwardData::sprite),
				Codec.STRING.fieldOf("discord_emote").forGetter(FullAwardData::discordEmote),
				Codec.STRING.fieldOf("tooltip").forGetter(FullAwardData::tooltip),
				Codec.STRING.fieldOf("submission_id").forGetter(FullAwardData::submissionId),
				AwardTier.CODEC.fieldOf("tier").forGetter(FullAwardData::tier)
		).apply(inst, FullAwardData::new));
	}
}
