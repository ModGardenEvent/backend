package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

public record Submission(String id,
                         String projectId,
                         String event,
                         String modrinthVersionId,
                         long submittedAt,
                         Date submissionDate) {
    public static final Codec<Submission> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Codec.STRING.fieldOf("project_id").forGetter(Submission::projectId),
            Event.ID_CODEC.fieldOf("event").forGetter(Submission::event),
            Codec.STRING.fieldOf("modrinth_version_id").forGetter(Submission::modrinthVersionId),
            Codec.LONG.fieldOf("submitted_at").forGetter(Submission::submittedAt),
            ExtraCodecs.DATE.fieldOf("submitted_date").forGetter(Submission::submissionDate)
    ).apply(inst, Submission::new));

    public static void getSubmission(Context ctx) {
        String path = ctx.pathParam("submission");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        Submission submission = innerQuery(path);
        if (submission == null) {
            ModGardenBackend.LOG.error("Could not find submission '{}'.", path);
            ctx.result("Could not find submission '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.info("Successfully queried submission from path '{}'", path);
        ctx.json(submission);
    }

    public static Submission innerQuery(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement())) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode submission from result set. ", ex);;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static String selectStatement() {
        return "SELECT " +
                "s.id, " +
                "s.project_id, " +
                "s.event, " +
                "s.modrinth_version_id, " +
                "s.submitted_at, " +
                "u.submitted_at AS submission_date, " +
                "FROM " +
                    "submissions AS s " +
                "WHERE " +
                    "s.id = ? " +
                "GROUP BY " +
                    "s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted_at";
    }
}
