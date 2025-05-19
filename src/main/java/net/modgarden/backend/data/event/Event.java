package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

public record Event(String id,
                    String slug,
                    String displayName,
					Optional<String> discordRoleId,
                    String minecraftVersion,
                    String loader,
					ZonedDateTime registrationTime,
                    ZonedDateTime startTime,
                    ZonedDateTime endTime) {
	// TODO: Endpoint for creating events.
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(1);
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
			Codec.STRING.optionalFieldOf("discord_role_id").forGetter(Event::discordRoleId),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Event::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Event::loader),
			ExtraCodecs.ISO_DATE_TIME.fieldOf("registration_time").forGetter(Event::registrationTime),
            ExtraCodecs.ISO_DATE_TIME.fieldOf("start_time").forGetter(Event::startTime),
			ExtraCodecs.ISO_DATE_TIME.fieldOf("end_time").forGetter(Event::endTime)
    ).apply(inst, Event::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Event::validateFromId);
	public static final Codec<String> SLUG_CODEC = Codec.STRING.validate(Event::validateFromSlug);
	public static final Codec<Event> FROM_ID_CODEC = ID_CODEC.xmap(Event::queryFromId, Event::id);
	public static final Codec<Event> FROM_SLUG_CODEC = SLUG_CODEC.xmap(Event::queryFromSlug, Event::slug);

    public static void getEvent(Context ctx) {
        String path = ctx.pathParam("event");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        Event event = query(path);
        if (event == null) {
            ModGardenBackend.LOG.debug("Could not find event '{}'.", path);
            ctx.result("Could not find event '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.debug("Successfully queried event from path '{}'", path);
        ctx.json(event);
    }

	public static void getCurrentRegistrationEvent(Context ctx) {
		Event event = null;
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement preparedStatement = connection.prepareStatement(selectStatement("e.registration_time <= ? AND e.end_time > ?", "start_time"))) {
			long currentMillis = System.currentTimeMillis();
			preparedStatement.setLong(1, currentMillis);
			preparedStatement.setLong(2, currentMillis);
			ResultSet result = preparedStatement.executeQuery();
			if (result.isBeforeFirst()) {
				event = new Event(
						result.getString("id"),
						result.getString("slug"),
						result.getString("display_name"),
						Optional.ofNullable(result.getString("discord_role_id")),
						result.getString("minecraft_version"),
						result.getString("loader"),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("registration_time")), ZoneId.of("GMT")),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT"))
				);
			}
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}

		if (event == null) {
			ModGardenBackend.LOG.debug("Could not find a current event with registration time active.");
			ctx.result("No current event with registration time active.");
			ctx.status(404);
			return;
		}

		ModGardenBackend.LOG.debug("Successfully queried a current event ({}) with registration time active.", event.slug);
		ctx.json(event);
	}

	public static void getCurrentDevelopmentEvent(Context ctx) {
		Event event = null;
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement preparedStatement = connection.prepareStatement(selectStatement("start_time <= ? AND end_time > ?", "start_time"))) {
			long currentMillis = System.currentTimeMillis();
			preparedStatement.setLong(1, currentMillis);
			preparedStatement.setLong(2, currentMillis);
			ResultSet result = preparedStatement.executeQuery();
			if (result.isBeforeFirst()) {
				event = new Event(
						result.getString("id"),
						result.getString("slug"),
						result.getString("display_name"),
						Optional.ofNullable(result.getString("discord_role_id")),
						result.getString("minecraft_version"),
						result.getString("loader"),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("registration_time")), ZoneId.of("GMT")),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")),
						ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT"))
				);
			}
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}

		if (event == null) {
			ModGardenBackend.LOG.debug("Could not find a current event with development time active.");
			ctx.result("No current event with development time active.");
			ctx.status(404);
			return;
		}

		ModGardenBackend.LOG.debug("Successfully queried a current event ({}) with development time active.", event.slug);
		ctx.json(event);
	}

	public static void getEvents(Context ctx) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var result = connection.createStatement().executeQuery("SELECT * FROM events");
			var events = new JsonArray();
			while (result.next()) {
				var event = new JsonObject();
				event.addProperty("id", result.getString("id"));
				event.addProperty("slug", result.getString("slug"));
				event.addProperty("display_name", result.getString("display_name"));

				if (result.getString("discord_role_id") != null) {
					event.addProperty("discord_role_id", result.getString("discord_role_id"));
				}

				event.addProperty("minecraft_version", result.getString("minecraft_version"));
				event.addProperty("loader", result.getLong("loader"));
				event.add("start_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")))
								.getOrThrow());
				event.add("end_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT")))
								.getOrThrow());
				events.add(event);
			}
			ctx.json(events);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	public static void getActiveEvents(Context ctx) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM events WHERE start_time <= ? AND end_time > ?")) {
			long currentMillis = System.currentTimeMillis();
			preparedStatement.setLong(1, currentMillis);
			preparedStatement.setLong(2, currentMillis);
			var result = preparedStatement.executeQuery();
			var events = new JsonArray();
			while (result.next()) {
				var event = new JsonObject();
				event.addProperty("id", result.getString("id"));
				event.addProperty("slug", result.getString("slug"));
				event.addProperty("display_name", result.getString("display_name"));

				if (result.getString("discord_role_id") != null) {
					event.addProperty("discord_role_id", result.getString("discord_role_id"));
				}

				event.addProperty("minecraft_version", result.getString("minecraft_version"));
				event.addProperty("loader", result.getLong("loader"));
				event.add("start_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")))
								.getOrThrow());
				event.add("end_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT")))
								.getOrThrow());
				events.add(event);
			}
			ctx.json(events);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

    @Nullable
    public static Event query(String path) {
        Event event = queryFromSlug(path.toLowerCase(Locale.ROOT));

        if (event == null)
            event = queryFromId(path);

        return event;
    }

    public static Event queryFromId(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement("e.id = ?", "id"))) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Event(
					result.getString("id"),
					result.getString("slug"),
					result.getString("display_name"),
					Optional.ofNullable(result.getString("discord_role_id")),
					result.getString("minecraft_version"),
					result.getString("loader"),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("registration_time")), ZoneId.of("GMT")),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT"))
			);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    public static Event queryFromSlug(String slug) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement("e.slug = ?", "slug"))) {
            prepared.setString(1, slug);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Event(
					result.getString("id"),
					result.getString("slug"),
					result.getString("display_name"),
					Optional.ofNullable(result.getString("discord_role_id")),
					result.getString("minecraft_version"),
					result.getString("loader"),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("registration_time")), ZoneId.of("GMT")),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT"))
			);
		} catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static DataResult<String> validateFromId(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM events WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get event with id '" + id + "'.");
	}

	private static DataResult<String> validateFromSlug(String slug) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM events WHERE slug = ?")) {
			prepared.setString(1, slug);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(slug);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get event with slug '" + slug + "'.");
	}

    private static String selectStatement(String whereStatement, String orderBy) {
		return """
				SELECT
					e.id,
					e.slug,
					e.display_name,
					e.discord_role_id,
					e.minecraft_version,
					e.loader,
					e.registration_time,
					e.start_time,
					e.end_time
				FROM events e
				WHERE"""
				+ " " + whereStatement + " " +
				"ORDER BY "
					+ orderBy +
				" LIMIT 1;";
    }
}
