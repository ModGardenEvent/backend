package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.data.profile.User;

import java.util.List;

public record AwardInstance(String awardId,
                            String awardedTo,
                            List<String> tooltipObjects) {
    public static final Codec<AwardInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Award.ID_CODEC.fieldOf("award_id").forGetter(AwardInstance::awardId),
            User.ID_CODEC.fieldOf("awarded_to").forGetter(AwardInstance::awardedTo),
            Codec.STRING.listOf().fieldOf("tooltip_objects").forGetter(AwardInstance::tooltipObjects)
    ).apply(inst, AwardInstance::new));

    public record UserValues(String awardId,
                             List<String> tooltipObjects) {
        public static final Codec<UserValues> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Award.ID_CODEC.fieldOf("award_id").forGetter(UserValues::awardId),
                Codec.STRING.listOf().fieldOf("tooltip_objects").forGetter(UserValues::tooltipObjects)
        ).apply(inst, UserValues::new));
    }
}
