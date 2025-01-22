package net.modgarden.backend.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
    private static Landing instance;

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

    public static Landing getInstance() {
        return instance;
    }

    public static void createInstance() {
        try (InputStream stream = ModGardenBackend.class.getResourceAsStream("/landing.json")) {
            if (stream == null) {
                ModGardenBackend.LOG.error("Could not find landing file.");
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                instance = CODEC.decode(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow().getFirst();
            }
        } catch (IOException | IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to handle landing file.", ex);
        }
    }
}
