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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Locale;

public record Event(String id,
                    String slug,
                    String displayName,
                    Date startDate) {
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
            ExtraCodecs.DATE.fieldOf("start_date").forGetter(Event::startDate)
    ).apply(inst, Event::new)));
    public static final Codec<Event> CODEC = Codec.STRING.xmap(Event::queryFromId, Event::id);

    public static void getEvent(Context ctx) {
        String path = ctx.pathParam("event");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        Event event = query(path.toLowerCase(Locale.ROOT));
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
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM submissions WHERE id=?")) {
            prepared.setString(1, id);
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, prepared.executeQuery()).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode submission from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            throw new NullPointerException("Could not find project inside project database.");
        }
    }

    public static Event queryFromSlug(String slug) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM submissions WHERE slug=?")) {
            prepared.setString(1, slug);
            ResultSet result = prepared.executeQuery();
            if (result == null)
                return null;
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode event from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }
}
