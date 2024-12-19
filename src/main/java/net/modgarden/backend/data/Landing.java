package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
}
