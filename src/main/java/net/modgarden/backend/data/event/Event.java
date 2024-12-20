package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

public record Event(String id,
                    String slug,
                    String displayName,
                    Date startDate,
                    List<Submission> submissions) {
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
            ExtraCodecs.DATE.fieldOf("date").forGetter(Event::startDate),
            Codec.withAlternative(Submission.DIRECT_CODEC, Submission.CODEC).listOf().optionalFieldOf("submissions", List.of()).forGetter(Event::submissions)
    ).apply(inst, Event::new)));
    public static final Codec<Event> CODEC = Codec.STRING.xmap(Event::queryFromId, Event::id);

    public static void getEvent(Context ctx) {
        String path = ctx.pathParam("event");
        Event event = query(path);
        if (event == null) {
            ModGardenBackend.LOG.error("Could not find event '{}'.", path);
            ctx.result("Could not find event '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, event).getOrThrow().toString(), Event.class));
    }

    @Nullable
    public static Event query(String path) {
        Event user = null;

        try {
            user = queryFromSlug(path);
        } catch (RuntimeException ignored) {}

        try {
            user = queryFromId(path);
        } catch (RuntimeException ignored) {}

        return user;
    }

    public static Event queryFromId(String id) {
        String query = "SELECT * FROM events WHERE id='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query);) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode event from result set. ", ex);;
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Event queryFromSlug(String slug) {
        String query = "SELECT * FROM events WHERE slug='" + slug + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        }  catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode event from result set. ", ex);;
            return null;
        }catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
