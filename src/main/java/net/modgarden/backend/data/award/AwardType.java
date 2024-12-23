package net.modgarden.backend.data.award;

import org.jetbrains.annotations.Nullable;

public record AwardType(String id,
                        String displayName,
                        String textureLocation,
                        String discordEmoteId,
                        @Nullable String tooltip) {

}
