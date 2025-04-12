package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

public record Event(String id,
                    String slug,
                    String displayName,
					String description,
                    String minecraftVersion,
                    String loader,
                    String loaderVersion,
                    ZonedDateTime startTime,
                    ZonedDateTime endTime) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(1);
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
			Codec.STRING.fieldOf("description").forGetter(Event::description),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Event::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Event::loader),
            Codec.STRING.fieldOf("loader_version").forGetter(Event::loaderVersion),
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

	public static void getEvents(Context ctx) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var result = connection.createStatement().executeQuery(selectAllStatement());
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("slug", result.getString("slug"));
				submission.addProperty("display_name", result.getString("display_name"));
				submission.addProperty("description", result.getString("description"));
				submission.addProperty("minecraft_version", result.getString("minecraft_version"));
				submission.addProperty("loader", result.getLong("loader"));
				submission.addProperty("loader_version", result.getLong("loader_version"));
				submission.add("start_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("start_time")), ZoneId.of("GMT")))
								.getOrThrow());
				submission.add("end_time",
						ExtraCodecs.ISO_DATE_TIME
								.encodeStart(JsonOps.INSTANCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("end_time")), ZoneId.of("GMT")))
								.getOrThrow());
				submissions.add(submission);
			}
			ctx.json(submissions);
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
             PreparedStatement prepared = connection.prepareStatement(selectStatement("id = ?"))) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Event(
					result.getString("id"),
					result.getString("slug"),
					result.getString("display_name"),
					result.getString("description"),
					result.getString("minecraft_version"),
					result.getString("loader"),
					result.getString("loader_version"),
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
             PreparedStatement prepared = connection.prepareStatement(selectStatement("slug = ?"))) {
            prepared.setString(1, slug);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Event(
					result.getString("id"),
					result.getString("slug"),
					result.getString("display_name"),
					result.getString("description"),
					result.getString("minecraft_version"),
					result.getString("loader"),
					result.getString("loader_version"),
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

    private static String selectStatement(String whereStatement) {
        return "SELECT " +
                "e.id, " +
                "e.slug, " +
                "e.display_name, " +
				"e.description, " +
                "e.minecraft_version, " +
                "e.loader, " +
                "e.loader_version, " +
                "e.start_time, " +
				"e.end_time " +
                "FROM " +
                    "events e " +
                "WHERE " +
                    "e." + whereStatement + " " +
                "GROUP BY " +
                    "e.id, e.slug, e.display_name, e.description, e.minecraft_version, e.loader, e.loader_version, e.start_time, e.end_time";
    }

	private static String selectAllStatement() {
		return "SELECT * FROM events";
	}
}
