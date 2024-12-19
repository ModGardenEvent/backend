package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Event(int id,
                    String slug,
                    String displayName,
                    Date startDate,
                    List<EventSubmission> submissions) {
    public static final Codec<Event> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("id").forGetter(Event::id),
            Codec.STRING.fieldOf("slug").forGetter(Event::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Event::displayName),
            ExtraCodecs.DATE.fieldOf("date").forGetter(Event::startDate),
            EventSubmission.CODEC.listOf().optionalFieldOf("submissions", List.of()).forGetter(Event::submissions)
    ).apply(inst, Event::new)));
    public static final Codec<Event> CODEC = Codec.INT.xmap(Event::queryFromId, Event::id);

    public static Event queryFromId(int id) {
        @Nullable Event existing = getFromId(id);
        if (existing != null)
            return existing;

        String query = "SELECT * FROM events WHERE id='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            String slug = resultSet.getString("slug");
            String displayName = resultSet.getString("display_name");
            Date startDate = resultSet.getTimestamp("start_date");
            int[] submissions = (int[]) resultSet.getArray("submissions").getArray();
            Event event = new Event(id, slug, displayName, startDate, Arrays.stream(submissions).mapToObj(EventSubmission::query).toList());
            return register(event);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Event queryFromSlug(String slug) {
        @Nullable Event existing = getFromSlug(slug);
        if (existing != null)
            return existing;

        String query = "SELECT * FROM events WHERE slug='" + slug + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(query)) {
                    int id = resultSet.getInt("id");
                    String displayName = resultSet.getString("display_name");
                    Date startDate = resultSet.getTimestamp("start_date");
                    int[] projects = (int[]) resultSet.getArray("projects").getArray();

                    connection.close();

                    Event event = new Event(id, slug, displayName, startDate, Arrays.stream(projects).mapToObj(EventSubmission::query).toList());
                    return register(event);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<Integer, Event> ID_TO_EVENT = new HashMap<>();
    private static final Map<String, Event> SLUG_TO_EVENT = new HashMap<>();

    private static Event getFromId(int id) {
        return ID_TO_EVENT.get(id);
    }

    private static Event getFromSlug(String slug) {
        return SLUG_TO_EVENT.get(slug);
    }

    private static Event register(Event project) {
        ID_TO_EVENT.put(project.id(), project);
        SLUG_TO_EVENT.put(project.slug(), project);
        return project;
    }
}
