package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public record Submission(String id,
                         String projectId,
                         String event,
                         String modrinthVersionId,
                         long submitted) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(3);
    public static final Codec<Submission> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Codec.STRING.fieldOf("project_id").forGetter(Submission::projectId),
            Event.ID_CODEC.fieldOf("event").forGetter(Submission::event),
            Codec.STRING.fieldOf("modrinth_version_id").forGetter(Submission::modrinthVersionId),
            Codec.LONG.fieldOf("submitted").forGetter(Submission::submitted)
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

        ModGardenBackend.LOG.debug("Successfully queried submission from path '{}'", path);
        ctx.json(submission);
    }

	public static void getSubmissionsByUser(Context ctx) {
		String user = ctx.pathParam("user");
		if (!user.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + user + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectByUserStatement(user);
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			ResultSet result = prepared.executeQuery();
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("project_id", result.getString("project_id"));
				submission.addProperty("event", result.getString("event"));
				submission.addProperty("modrinth_version_id", result.getString("modrinth_version_id"));
				submission.addProperty("submitted_at", result.getLong("submitted_at"));
				submissions.add(submission);
			}
			ctx.json(submissions);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	public static void getSubmissionsByEvent(Context ctx) {
		String event = ctx.pathParam("event");
		if (!event.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + event + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectByEventStatement(event);
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			ResultSet result = prepared.executeQuery();
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("project_id", result.getString("project_id"));
				submission.addProperty("event", result.getString("event"));
				submission.addProperty("modrinth_version_id", result.getString("modrinth_version_id"));
				submission.addProperty("submitted_at", result.getLong("submitted_at"));
				submissions.add(submission);
			}
			ctx.json(submissions);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}


    public static Submission innerQuery(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement())) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Submission(
					result.getString("id"),
					result.getString("project_id"),
					result.getString("event"),
					result.getString("modrinth_version_id"),
					result.getLong("submitted_at")
			);
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
                "s.submitted_at " +
                "FROM " +
                    "submissions s " +
                "WHERE " +
                    "s.id = ? " +
                "GROUP BY " +
                    "s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted_at";
    }

	private static String selectByUserStatement(String user) {
		return """
				SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted_at
				FROM submissions s
					LEFT JOIN projects p on p.id = s.project_id
					LEFT JOIN project_authors a on a.project_id = s.project_id
					WHERE  p.id IN (SELECT pa.project_id
									FROM   project_authors pa
										   JOIN users uu
											 ON pa.user_id = uu.id
									WHERE  uu.id = '%s'
											OR uu.username = '%s')
					GROUP BY s.id
				""".formatted(user, user);
	}

	private static String selectByEventStatement(String event) {
		return """
			SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted_at
			FROM submissions s
				LEFT JOIN events e on e.id = s.event
				WHERE s.event = '%s' OR e.slug = '%s'
				GROUP BY s.id
			""".formatted(event, event);
	}
}
