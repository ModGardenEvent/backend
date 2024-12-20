package net.modgarden.backend.util;

import com.mojang.serialization.Codec;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

public class ExtraCodecs {
    public static final Codec<Date> DATE = Codec.STRING.xmap(string -> {
        try {
            return DateFormat.getDateTimeInstance().parse(string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }, date -> DateFormat.getDateTimeInstance().format(date));

    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(string -> new UUID(
            new BigInteger(string.substring(0, 16), 16).longValue(),
            new BigInteger(string.substring(16), 16).longValue()
    ), uuid -> uuid.toString().replace("-", ""));
}
