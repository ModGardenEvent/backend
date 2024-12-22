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
import java.util.Locale;

public record Submission(String id,
                         String projectId,
                         String modrinthVersionId,
                         Event event,
                         Date submittedAt) {
    public static final Codec<Submission> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Codec.STRING.fieldOf("project_id").forGetter(Submission::projectId),
            Codec.STRING.fieldOf("modrinth_version_id").forGetter(Submission::modrinthVersionId),
            Event.CODEC.fieldOf("event").forGetter(Submission::event),
            ExtraCodecs.DATE.fieldOf("submitted_at").forGetter(Submission::submittedAt)
    ).apply(inst, Submission::new));

    public static void getSubmission(Context ctx) {
        String path = ctx.pathParam("submission");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        Submission submission = innerQuery(path.toLowerCase(Locale.ROOT));
        if (submission == null) {
            ModGardenBackend.LOG.error("Could not find submission '{}'.", path);
            ctx.result("Could not find submission '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(submission);
    }

    public static Submission innerQuery(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM submissions WHERE id=?")) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result == null)
                return null;
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode submission from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }
}
