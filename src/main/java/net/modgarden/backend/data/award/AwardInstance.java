package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.profile.User;

public record AwardInstance(String awardId,
                            String awardedTo,
                            String customData) {
    public static final Codec<AwardInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Award.ID_CODEC.fieldOf("award_id").forGetter(AwardInstance::awardId),
            User.ID_CODEC.fieldOf("awarded_to").forGetter(AwardInstance::awardedTo),
            Codec.STRING.fieldOf("custom_data").forGetter(AwardInstance::customData)
    ).apply(inst, AwardInstance::new));

    public record UserValues(String awardId,
                             String customData) {
        public static final Codec<UserValues> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Award.ID_CODEC.fieldOf("award_id").forGetter(UserValues::awardId),
                Codec.STRING.fieldOf("custom_data").forGetter(UserValues::customData)
        ).apply(inst, UserValues::new));
    }
}
