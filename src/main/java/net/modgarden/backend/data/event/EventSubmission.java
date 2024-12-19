package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public record EventSubmission(int id,
                              String projectId,
                              Event event,
                              Date submittedAt) {
    public static final Codec<EventSubmission> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("id").forGetter(EventSubmission::id),
            Codec.STRING.fieldOf("project_id").forGetter(EventSubmission::projectId),
            Event.CODEC.fieldOf("event").forGetter(EventSubmission::event),
            ExtraCodecs.DATE.fieldOf("submitted_at").forGetter(EventSubmission::submittedAt)
    ).apply(inst, EventSubmission::new));


    public static EventSubmission query(int id) {
        String query = "SELECT * FROM submissions WHERE id='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            String projectId = resultSet.getString("project_id");
            int eventId = resultSet.getInt("event");
            Date submittedAt = resultSet.getDate("submitted_at");
            EventSubmission event = new EventSubmission(id, projectId, Event.queryFromId(eventId), submittedAt);
            return register(event);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<Integer, EventSubmission> ID_TO_SUBMISSION = new HashMap<>();

    private static EventSubmission get(int id) {
        return ID_TO_SUBMISSION.get(id);
    }

    private static EventSubmission register(EventSubmission submission) {
        ID_TO_SUBMISSION.put(submission.id(), submission);
        return submission;
    }
}
