package net.modgarden.backend.data.event;

import static java.util.Map.entry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.Converter;
import net.modgarden.backend.util.ExtraCodecs;

public record Event(String id,
                    String eventSlug,
                    String genreSlug,
                    String displayName,
                    Optional<String> roleId,
                    String minecraftVersion,
                    String loader,
                    Instant registrationOpenTime,
                    Instant registrationCloseTime,
                    Instant startTime,
                    Instant endTime,
                    Instant freezeTime) {
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("event_slug").forGetter(Event::eventSlug),
			Codec.STRING.fieldOf("genre_slug").forGetter(Event::genreSlug),
			Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
			Codec.STRING.optionalFieldOf("role_id").forGetter(Event::roleId),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Event::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Event::loader),
			ExtraCodecs.INSTANT_CODEC.fieldOf("registration_open_time").forGetter(Event::registrationOpenTime),
			ExtraCodecs.INSTANT_CODEC.fieldOf("registration_close_time").forGetter(Event::registrationCloseTime),
            ExtraCodecs.INSTANT_CODEC.fieldOf("start_time").forGetter(Event::startTime),
			ExtraCodecs.INSTANT_CODEC.fieldOf("end_time").forGetter(Event::endTime),
			ExtraCodecs.INSTANT_CODEC.fieldOf("freeze_time").forGetter(Event::freezeTime)
    ).apply(inst, Event::new)));
	// TODO: make ApiFormat and Event identical so the former is unnecessary
	public static final Converter<Event, ApiFormat> COMAP = event -> new ApiFormat(
			event.id(),
			event.eventSlug(),
			new Metadata(event.displayName(), "To-do"),
			event.registrationOpenTime(),
			event.registrationCloseTime(),
			event.startTime(),
			event.endTime(),
			event.freezeTime(),
			new ApiFormat.MinecraftPlatform(event.loader(), event.minecraftVersion())
	);
	public static final Converter<ApiFormat, Event> MAP = apiFormat -> new Event(
			apiFormat.id(),
			apiFormat.slug(),
			"mod-garden",
			apiFormat.metadata().name(),
			Optional.empty(),
			((ApiFormat.MinecraftPlatform) apiFormat.platform()).gameVersion(),
			((ApiFormat.MinecraftPlatform) apiFormat.platform()).modLoader(),
			apiFormat.registrationOpenTime(),
			apiFormat.registrationCloseTime(),
			apiFormat.startTime(),
			apiFormat.endTime(),
			apiFormat.freezeTime()
	);
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Event::validate);

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM themes WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get event with id '" + id + "'");
	}

	public record ApiFormat(
			String id,
			String slug,
			Metadata metadata,
			Instant registrationOpenTime,
			Instant registrationCloseTime,
			Instant startTime,
			Instant endTime,
			Instant freezeTime,
			Platform platform
	) {
		private static final Map<String, MapCodec<Platform>> PLATFORM_MAP_CODECS = Map.ofEntries(
				entry("minecraft", Platform.fromMapCodec(MinecraftPlatform.CODEC))
		);
		private static final Codec<Platform> PLATFORM_CODEC = Codec.STRING.dispatch("game", Platform::game, PLATFORM_MAP_CODECS::get);
		public static final Codec<ApiFormat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ID_CODEC.fieldOf("id").forGetter(ApiFormat::id),
				Codec.STRING.fieldOf("slug").forGetter(ApiFormat::slug),
				Metadata.CODEC.fieldOf("metadata").forGetter(ApiFormat::metadata),
				ExtraCodecs.INSTANT_CODEC.fieldOf("registration_open_time").forGetter(ApiFormat::registrationOpenTime),
				ExtraCodecs.INSTANT_CODEC.fieldOf("registration_close_time").forGetter(ApiFormat::registrationCloseTime),
				ExtraCodecs.INSTANT_CODEC.fieldOf("start_time").forGetter(ApiFormat::startTime),
				ExtraCodecs.INSTANT_CODEC.fieldOf("end_time").forGetter(ApiFormat::endTime),
				ExtraCodecs.INSTANT_CODEC.fieldOf("freeze_time").forGetter(ApiFormat::freezeTime),
				PLATFORM_CODEC.fieldOf("platform").forGetter(ApiFormat::platform)
		).apply(instance, ApiFormat::new));

		public interface Platform {
			String game();
			MapCodec<? extends Platform> getCodec();

			static <T extends Platform> MapCodec<Platform> fromMapCodec(MapCodec<T> codec) {
				//noinspection unchecked
				return codec.xmap(
						t -> t,
						metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
				);
			}
		}

		public record MinecraftPlatform(
				String modLoader,
				String gameVersion
		) implements Platform {
			private static final MapCodec<MinecraftPlatform> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
					Codec.STRING.fieldOf("mod_loader").forGetter(MinecraftPlatform::modLoader),
					Codec.STRING.fieldOf("game_version").forGetter(MinecraftPlatform::gameVersion)
			).apply(instance, MinecraftPlatform::new));

			@Override
			public String game() {
				return "minecraft";
			}

			@Override
			public MapCodec<? extends Platform> getCodec() {
				return CODEC;
			}
		}
	}
}
