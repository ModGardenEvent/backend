package net.modgarden.backend.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

public class ExtraCodecs {
    public static final Codec<Date> DATE = Codec.either(Codec.LONG, Codec.STRING).flatXmap(either -> either.map(l -> DataResult.success(new Date(l)),string -> {
        try {
            return DataResult.success(DateFormat.getDateTimeInstance().parse(string));
        } catch (ClassCastException | ParseException ignored) {
        }
        return DataResult.error(() -> "Failed to parse date.");
    }),
            date -> DataResult.success(Either.right(DateFormat.getDateTimeInstance().format(date))));

    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(string -> new UUID(
            new BigInteger(string.substring(0, 16), 16).longValue(),
            new BigInteger(string.substring(16), 16).longValue()
    ), uuid -> uuid.toString().replace("-", ""));
}
