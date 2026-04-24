package net.modgarden.backend.data.user;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public record Bio(@Nullable String displayName,
				  @Nullable String pronouns,
				  @Nullable String description,
				  @Nullable String avatarUrl,
				  Map<String, String> fields) {
	public static final Codec<Bio> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.optionalFieldOf("display_name").forGetter(bio -> Optional.ofNullable(bio.displayName())),
			Codec.STRING.optionalFieldOf("pronouns").forGetter(bio -> Optional.ofNullable(bio.pronouns())),
			Codec.STRING.optionalFieldOf("description").forGetter(bio -> Optional.ofNullable(bio.description())),
			Codec.STRING.optionalFieldOf("avatar_url").forGetter(bio -> Optional.ofNullable(bio.avatarUrl())),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("fields", Collections.emptyMap()).forGetter(Bio::fields)
	).apply(inst, (displayName, pronouns, description, avatarUrl, fields) -> new Bio(displayName.orElse(null), pronouns.orElse(null), description.orElse(null), avatarUrl.orElse(null), fields)));
}
