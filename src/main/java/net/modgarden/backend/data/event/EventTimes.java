package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.codec.ExtraCodecs;
import net.modgarden.backend.util.codec.NullableCodec;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

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

	public record Modifiable(@Nullable Instant registrationOpen,
	                         @Nullable Instant registrationClose,
	                         @Nullable Instant developmentStart,
	                         @Nullable Instant developmentEnd,
	                         @Nullable Instant packFreeze) {
		public static final Codec<EventTimes.Modifiable> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				ExtraCodecs.INSTANT_CODEC
						.optionalFieldOf("registration_open")
						.forGetter(et -> Optional.ofNullable(et.registrationOpen)),
				ExtraCodecs.INSTANT_CODEC
						.optionalFieldOf("registration_close")
						.forGetter(et -> Optional.ofNullable(et.registrationClose)),
				ExtraCodecs.INSTANT_CODEC
						.optionalFieldOf("development_start")
						.forGetter(et -> Optional.ofNullable(et.developmentStart)),
				ExtraCodecs.INSTANT_CODEC
						.optionalFieldOf("development_end")
						.forGetter(et -> Optional.ofNullable(et.developmentEnd)),
				ExtraCodecs.INSTANT_CODEC
						.optionalFieldOf("pack_freeze")
						.forGetter(et -> Optional.ofNullable(et.packFreeze))
		).apply(inst, (registrationOpen, registrationClose, developmentStart, developmentEnd, packFreeze) ->
				new Modifiable(registrationOpen.orElse(null), registrationClose.orElse(null), developmentStart.orElse(null), developmentEnd.orElse(null), packFreeze.orElse(null))));
	}
}
