package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// TODO: Move award roles to a list.
public record EventRoles(@Nullable String participant,
                         @Nullable String themeAward,
                         @Nullable String teamPickAward) {
	public static final Codec<EventRoles> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.optionalFieldOf("participant").forGetter(roles -> Optional.ofNullable(roles.participant)),
			Codec.STRING.optionalFieldOf("theme_award").forGetter(roles -> Optional.ofNullable(roles.themeAward)),
			Codec.STRING.optionalFieldOf("team_pick_award").forGetter(roles -> Optional.ofNullable(roles.teamPickAward))
	).apply(inst, (participant, themeAward, teamPickAward) ->
			new EventRoles(participant.orElse(null), themeAward.orElse(null), teamPickAward.orElse(null))));
}
