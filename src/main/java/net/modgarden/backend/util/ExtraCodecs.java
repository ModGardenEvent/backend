package net.modgarden.backend.util;

import com.mojang.serialization.Codec;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ExtraCodecs {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(string -> new UUID(
            new BigInteger(string.substring(0, 16), 16).longValue(),
            new BigInteger(string.substring(16), 16).longValue()
    ), uuid -> uuid.toString().replace("-", ""));
	public static final Codec<ZonedDateTime> ISO_DATE_TIME = Codec
			.withAlternative(Codec.STRING, Codec.LONG, timestamp ->
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("GMT")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
			).xmap(timestamp -> ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME), time -> time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
}
