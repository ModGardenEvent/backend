package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

public record Submission(String id,
                         String projectId,
                         Event event,
                         Date submittedAt) {
    public static final Codec<Submission> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Codec.STRING.fieldOf("project_id").forGetter(Submission::projectId),
            Event.CODEC.fieldOf("event").forGetter(Submission::event),
            ExtraCodecs.DATE.fieldOf("submitted_at").forGetter(Submission::submittedAt)
    ).apply(inst, Submission::new));
    public static final Codec<Submission> CODEC = Codec.STRING.xmap(Submission::query, Submission::id);

    public static void getSubmission(Context ctx) {
        String path = ctx.pathParam("submission");
        Submission submission = query(path);
        if (submission == null) {
            ModGardenBackend.LOG.error("Could not find submission '{}'.", path);
            ctx.result("Could not find submission '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, submission).getOrThrow().toString(), Event.class));
    }

    public static Submission query(String id) {
        String query = "SELECT * FROM submissions WHERE id='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode submission from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            throw new NullPointerException("Could not find project inside project database.");
        }
    }
}
