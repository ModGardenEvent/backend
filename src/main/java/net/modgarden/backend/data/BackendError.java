package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BackendError(String error, String description) {
    public static final Codec<BackendError> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("error").forGetter(BackendError::error),
            Codec.STRING.fieldOf("description").forGetter(BackendError::description)
    ).apply(inst, BackendError::new));
}
