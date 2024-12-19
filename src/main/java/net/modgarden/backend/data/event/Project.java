package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Project(String id,
                      String primaryAuthor,
                      List<User> authors,
                      List<Event> events) {
    public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("attributed_to").forGetter(Project::primaryAuthor),
            User.CODEC.listOf().optionalFieldOf("authors", List.of()).forGetter(Project::authors),
            Event.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Project::events)
    ).apply(inst, Project::new)));
    public static final Codec<Project> CODEC = Codec.STRING.xmap(Project::query, Project::id);

    public static Project query(String id) {
        @Nullable Project existing = get(id);
        if (existing != null)
            return existing;

        String query = "SELECT * FROM projects WHERE id='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            String primaryAuthor = resultSet.getString("primary_author");
            String[] authorIds = (String[]) resultSet.getArray("authors").getArray();
            int[] eventIds = (int[]) resultSet.getArray("events").getArray();

            Project project = new Project(id, primaryAuthor, Arrays.stream(authorIds).map(User::queryFromId).toList(), Arrays.stream(eventIds).mapToObj(Event::queryFromId).toList());
            return register(project);
        } catch (SQLException ex) {
            throw new NullPointerException("Could not find project inside project database.");
        }
    }

    private static final Map<String, Project> ID_TO_PROJECT = new HashMap<>();

    private static Project get(String id) {
        return ID_TO_PROJECT.get(id);
    }

    private static Project register(Project project) {
        ID_TO_PROJECT.put(project.id(), project);
        return project;
    }
}
