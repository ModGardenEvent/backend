package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Locale;

public record Event(String id,
                    String slug,
                    String displayName,
                    String description,
                    Date startDate) {
    public static final Codec<Event> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
            Codec.STRING.fieldOf("description").forGetter(Event::description),
            ExtraCodecs.DATE.fieldOf("start_date").forGetter(Event::startDate)
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
            ModGardenBackend.LOG.error("Could not find event '{}'.", path);
            ctx.result("Could not find event '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(event);
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
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode submission from result set. ", ex);
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
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode event from result set. ", ex);
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
                "e.description, " +
                "e.start_date " +
                "FROM " +
                "events e " +
                "WHERE " +
                "e." + whereStatement + " " +
                "GROUP BY " +
                "e.id, e.slug, e.display_name, e.description, e.start_date";
    }
}
