package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;

import java.io.InputStream;

public record Landing(String homepage,
                      String discord,
                      String name,
                      String version) {
    public static final Codec<Landing> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("homepage").forGetter(Landing::homepage),
            Codec.STRING.fieldOf("discord").forGetter(Landing::discord),
            Codec.STRING.fieldOf("name").forGetter(Landing::name),
            Codec.STRING.fieldOf("version").forGetter(Landing::version)
    ).apply(inst, Landing::new));
    private static Landing instance = null;

    public static void getLandingJson(Context ctx) {
        if (instance == null) {
            InputStream landingFile = ModGardenBackend.class.getResourceAsStream("/landing.json");
            if (landingFile == null) {
                ModGardenBackend.LOG.error("Could not find 'landing.json' resource file.");
                ctx.result("Could not find landing file.");
                ctx.status(404);
                return;
            }
            instance = ctx.jsonMapper().fromJsonStream(landingFile, Landing.class);
        }

        ctx.json(instance);
    }
}
