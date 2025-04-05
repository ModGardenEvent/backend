package net.modgarden.backend.data.event;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public record Event(String id,
                    String slug,
                    String displayName,
                    String minecraftVersion,
                    String loader,
                    String loaderVersion,
                    long started) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(1);
    public static final Codec<Event> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
            Codec.STRING.fieldOf("minecraft_version").forGetter(Event::minecraftVersion),
            Codec.STRING.fieldOf("loader").forGetter(Event::loader),
            Codec.STRING.fieldOf("loader_version").forGetter(Event::loaderVersion),
            Codec.LONG.fieldOf("started").forGetter(Event::started)
    ).apply(inst, Event::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Event::validate);

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
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			var result = connection.createStatement().executeQuery(selectAllStatement());
			var json_result = JsonParser.parseString(result.getString("json_result"));
			ctx.json(json_result);
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
					result.getString("minecraft_version"),
					result.getString("loader"),
					result.getString("loader_version"),
					result.getLong("started")
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
					result.getString("minecraft_version"),
					result.getString("loader"),
					result.getString("loader_version"),
					result.getLong("started")
			);
		} catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static DataResult<String> validate(String id) {
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

    private static String selectStatement(String whereStatement) {
        return "SELECT " +
                "e.id, " +
                "e.slug, " +
                "e.display_name, " +
                "e.minecraft_version, " +
                "e.loader, " +
                "e.loader_version, " +
                "e.started " +
                "FROM " +
                    "events e " +
                "WHERE " +
                    "e." + whereStatement + " " +
                "GROUP BY " +
                    "e.id, e.slug, e.display_name, e.minecraft_version, e.loader, e.loader_version, e.started";
    }

	private static String selectAllStatement() {
		return """
			SELECT json_group_array(
					json_object(
						'id', id,
						'slug', slug,
						'display_name', display_name,
						'minecraft_version', minecraft_version,
						'loader', loader,
						'loader_version', loader_version,
						'started', started
					)
				) AS json_result
			FROM events;
			""";
	}
}
