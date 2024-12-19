package net.modgarden.backend.data.award;

import org.jetbrains.annotations.Nullable;

public record AwardType(int id,
                        String displayName,
                        String textureLocation,
                        @Nullable String tooltip) {

}
