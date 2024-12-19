package net.modgarden.backend.util;

import com.mojang.serialization.Codec;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public class ExtraCodecs {
    public static final Codec<Date> DATE = Codec.STRING.xmap(string -> {
        try {
            return DateFormat.getDateTimeInstance().parse(string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }, date -> DateFormat.getDateTimeInstance().format(date));
}
