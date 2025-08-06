package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

// TODO: Potentially allow GitHub only submissions. Not necessarily now, but more notes on this will be placed in internal team chats. - Calico
public record Submission(String id,
                         String event,
						 String projectId,
                         String modrinthVersionId,
						 ZonedDateTime submitted) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(3);
    public static final Codec<Submission> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Submission::id),
            Event.ID_CODEC.fieldOf("event").forGetter(Submission::event),
			Codec.STRING.fieldOf("project_id").forGetter(Submission::projectId),
            Codec.STRING.fieldOf("modrinth_version_id").forGetter(Submission::modrinthVersionId),
            ExtraCodecs.ISO_DATE_TIME.fieldOf("submitted").forGetter(Submission::submitted)
    ).apply(inst, Submission::new));
	public static final Codec<String> ID_CODEC = Codec.STRING.validate(Submission::validate);
	public static final Codec<Submission> CODEC = ID_CODEC.xmap(Submission::innerQuery, Submission::id);

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
		var queryString = selectByUserStatement();
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			prepared.setString(1, user);
			prepared.setString(2, user);
			ResultSet result = prepared.executeQuery();
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("event", result.getString("event"));
				submission.addProperty("project_id", result.getString("project_id"));
				submission.addProperty("modrinth_version_id", result.getString("modrinth_version_id"));
				submission.addProperty("submitted", result.getLong("submitted"));
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
		var queryString = selectByEventStatement();
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			prepared.setString(1, event);
			prepared.setString(2, event);
			ResultSet result = prepared.executeQuery();
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("event", result.getString("event"));
				submission.addProperty("project_id", result.getString("project_id"));
				submission.addProperty("modrinth_version_id", result.getString("modrinth_version_id"));
				submission.addProperty("submitted", result.getLong("submitted"));
				submissions.add(submission);
			}
			ctx.json(submissions);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	public static void getSubmissionsByUserAndEvent(Context ctx) {
		String user = ctx.pathParam("user");
		String event = ctx.pathParam("event");
		if (!user.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + user + "'.");
			ctx.status(422);
			return;
		}
		if (!event.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + event + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectByUserAndEventStatement();
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			prepared.setString(1, user);
			prepared.setString(2, user);
			prepared.setString(3, event);
			prepared.setString(4, event);
			ResultSet result = prepared.executeQuery();
			var submissions = new JsonArray();
			while (result.next()) {
				var submission = new JsonObject();
				submission.addProperty("id", result.getString("id"));
				submission.addProperty("event", result.getString("event"));
				submission.addProperty("project_id", result.getString("project_id"));
				submission.addProperty("modrinth_version_id", result.getString("modrinth_version_id"));
				submission.addProperty("submitted", result.getLong("submitted"));
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
					result.getString("event"),
					result.getString("project_id"),
					result.getString("modrinth_version_id"),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("submitted")), ZoneId.of("GMT"))
			);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static String selectStatement() {
        return """
					SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted
					FROM
						submissions s
					WHERE
						s.id = ?
					GROUP BY
						s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted
				""";
    }

	private static String selectByUserStatement() {
		return """
			SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted
			FROM submissions s
				LEFT JOIN projects p on p.id = s.project_id
				LEFT JOIN project_authors a on a.project_id = s.project_id
				WHERE p.id IN (SELECT pa.project_id
					FROM project_authors pa
						JOIN users uu
							ON pa.user_id = uu.id
						WHERE  uu.id = ?
							OR uu.username = ?)
				GROUP BY s.id
			""";
	}

	private static String selectByEventStatement() {
		return """
			SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted
			FROM submissions s
				LEFT JOIN events e on e.id = s.event
				WHERE s.event = ? OR e.slug = ?
				GROUP BY s.id
			""";
	}

	private static String selectByUserAndEventStatement() {
		return """
				SELECT s.id, s.project_id, s.event, s.modrinth_version_id, s.submitted
				FROM submissions s
					LEFT JOIN projects p on p.id = s.project_id
					LEFT JOIN project_authors a on a.project_id = s.project_id
					LEFT JOIN events e on e.id = s.event
					WHERE p.id IN (SELECT pa.project_id
						FROM project_authors pa
							JOIN users uu
								ON pa.user_id = uu.id
							WHERE  uu.id = ?
								OR uu.username = ?) AND
						s.event = ? OR e.slug = ?
					GROUP BY s.id
				""";
	}

	private static DataResult<String> validate(String id) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM submissions WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get project with id '" + id + "'.");
	}
}
