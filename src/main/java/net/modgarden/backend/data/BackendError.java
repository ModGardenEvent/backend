package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;

import java.util.Locale;

public record BackendError(String error, String description) {
    public static final Codec<BackendError> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("error").forGetter(BackendError::error),
            Codec.STRING.fieldOf("description").forGetter(BackendError::description)
    ).apply(inst, BackendError::new));

    public BackendError(String error, String description) {
        this.error = error.toLowerCase(Locale.ROOT).replace(" ", "_");
        this.description = description;
    }

    public static void handleError(Context ctx) {
        ctx.json(new BackendError(ctx.status().getMessage(), ctx.result()));
    }
}
