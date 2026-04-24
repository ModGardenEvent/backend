package net.modgarden.backend.data;

import java.util.Locale;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;

public record ExceptionPage(String error, String description) {
    public static final Codec<ExceptionPage> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("error").forGetter(ExceptionPage::error),
            Codec.STRING.fieldOf("description").forGetter(ExceptionPage::description)
    ).apply(inst, ExceptionPage::new));

    public ExceptionPage(String error, String description) {
        this.error = error.toLowerCase(Locale.ROOT).replace(" ", "_");
        this.description = description;
    }

    public static void handleError(Context ctx) {
	    String result = ctx.result();
	    ctx.json(new ExceptionPage(
			    ctx.status().getMessage(),
			    Objects.requireNonNullElse(result, "Result is null")
	    ));
    }
}
