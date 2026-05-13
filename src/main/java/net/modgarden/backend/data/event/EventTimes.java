package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.util.ExtraCodecs;

import java.time.Instant;

public record EventTimes(Instant registrationOpen,
						 Instant registrationClose,
						 Instant developmentStart,
						 Instant developmentEnd,
						 Instant packFreeze) {
	public static final Codec<EventTimes> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			ExtraCodecs.INSTANT_CODEC.fieldOf("registration_open").forGetter(EventTimes::registrationOpen),
			ExtraCodecs.INSTANT_CODEC.fieldOf("registration_close").forGetter(EventTimes::registrationClose),
			ExtraCodecs.INSTANT_CODEC.fieldOf("development_start").forGetter(EventTimes::developmentStart),
			ExtraCodecs.INSTANT_CODEC.fieldOf("development_end").forGetter(EventTimes::developmentEnd),
			ExtraCodecs.INSTANT_CODEC.fieldOf("pack_freeze").forGetter(EventTimes::packFreeze)
	).apply(inst, EventTimes::new));
}
