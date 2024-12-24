package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.profile.User;

import java.util.Optional;

public record AwardInstance(String awardId,
                            String awardedTo,
                            Optional<String> additionalTooltip) {
    public static final Codec<AwardInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Award.ID_CODEC.fieldOf("award_id").forGetter(AwardInstance::awardId),
            User.ID_CODEC.fieldOf("awarded_to").forGetter(AwardInstance::awardedTo),
            Codec.STRING.optionalFieldOf("additional_tooltip").forGetter(AwardInstance::additionalTooltip)
    ).apply(inst, AwardInstance::new));

    public record UserValues(String awardId,
                             Optional<String> additionalTooltip) {
        public static final Codec<UserValues> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Award.ID_CODEC.fieldOf("award_id").forGetter(UserValues::awardId),
                Codec.STRING.optionalFieldOf("additional_tooltip").forGetter(UserValues::additionalTooltip)
        ).apply(inst, UserValues::new));
    }
}
