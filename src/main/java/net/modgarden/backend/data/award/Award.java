package net.modgarden.backend.data.award;

import org.jetbrains.annotations.Nullable;

public record Award(AwardType type,
                    @Nullable String additionalTooltip) {

}
