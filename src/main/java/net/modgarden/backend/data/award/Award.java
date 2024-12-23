package net.modgarden.backend.data.award;

import org.jetbrains.annotations.Nullable;

public record Award(String id,
                    String typeId,
                    @Nullable String additionalTooltip) {

}
