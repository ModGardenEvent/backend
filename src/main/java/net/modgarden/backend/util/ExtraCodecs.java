package net.modgarden.backend.util;

import com.mojang.serialization.Codec;

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

    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
}
