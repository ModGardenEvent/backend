package net.modgarden.backend.data.event;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Genre(
		String id,
		String slug,
		EventMetadata metadata,
		List<String> events
) {
	public static final Codec<String> ID_CODEC = Codec.STRING.validate(Genre::validate);
	public static final Codec<Genre> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
			ID_CODEC.fieldOf("id").forGetter(Genre::id),
			Codec.STRING.fieldOf("slug").forGetter(Genre::slug),
			EventMetadata.CODEC.fieldOf("metadata").forGetter(Genre::metadata),
			Codec.list(Event.ID_CODEC).fieldOf("events").forGetter(Genre::events)
	).apply(instance, Genre::new)));
	public static Genre MOD_GARDEN;

	private static DataResult<String> validate(String id) {
		if ("modgr".equals(id)) {
			return DataResult.success(id);
		} else {
			return DataResult.error(() -> id + " is not a valid genre ID");
		}
	}

	public static Genre getModGarden(List<String> events) {
		return new Genre(
				"modgr",
				"mod-garden",
				new EventMetadata("Mod Garden", "A multi-month long event where developers get together to develop a mod within a 2-month timeframe and showcase their mods in a server environment."),
				events
		);
	}
}
