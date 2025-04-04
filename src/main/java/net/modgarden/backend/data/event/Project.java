package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public record Project(String id,
                      String slug,
                      String modrinthId,
                      String attributedTo,
                      List<String> authors) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(2);
    public static final Codec<Project> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("slug").forGetter(Project::slug),
            Codec.STRING.fieldOf("modrinth_id").forGetter(Project::modrinthId),
            User.ID_CODEC.fieldOf("attributed_to").forGetter(Project::attributedTo),
            User.ID_CODEC.listOf().fieldOf("authors").forGetter(Project::authors)
    ).apply(inst, Project::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Project::validate);

    public static void getProject(Context ctx) {
        String path = ctx.pathParam("project");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        // TODO: Allow Modrinth as a service.
        Project project = innerQuery(path);
        if (project == null) {
            ModGardenBackend.LOG.error("Could not find project '{}'.", path);
            ctx.result("Could not find project '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.debug("Successfully queried project from path '{}'", path);
        ctx.json(project);
    }

    private static Project innerQuery(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement("id = ?"))) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			List<String> authors = List.of(result.getString("authors").split(","));
			return new Project(
				result.getString("id"),
				result.getString("slug"),
				result.getString("modrinth_id"),
				result.getString("attributed_to"),
				authors
			);
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode project from result set. ", ex);;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

	public static void getProjectsByUser(Context ctx) {
		String user = ctx.pathParam("user");
		if (!user.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + user + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectAllByUser(user);
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			ResultSet result = prepared.executeQuery();
			var projectList = new JsonArray();
			while (result.next()) {
				var projectObject = new JsonObject();
				var authors = new JsonArray();

				for (String author : result.getString("authors").split(",")) {
					authors.add(author);
				}
				projectObject.addProperty("id", result.getString("id"));
				projectObject.addProperty("slug", result.getString("slug"));
				projectObject.addProperty("modrinth_id", result.getString("modrinth_id"));
				projectObject.addProperty("attributed_to", result.getString("attributed_to"));
				projectObject.add("authors", authors);
				projectList.add(projectObject);
			}


			ctx.json(projectList);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	public static void getProjectsByEvent(Context ctx) {
		String event = ctx.pathParam("event");
		if (!event.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + event + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectAllByEvent(event);
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			PreparedStatement prepared = connection.prepareStatement(queryString);
			ResultSet result = prepared.executeQuery();
			var projectList = new JsonArray();
			while (result.next()) {
				var projectObject = new JsonObject();
				var authors = new JsonArray();

				for (String author : result.getString("authors").split(",")) {
					authors.add(author);
				}
				projectObject.addProperty("id", result.getString("id"));
				projectObject.addProperty("slug", result.getString("slug"));
				projectObject.addProperty("modrinth_id", result.getString("modrinth_id"));
				projectObject.addProperty("attributed_to", result.getString("attributed_to"));
				projectObject.add("authors", authors);
				projectList.add(projectObject);
			}


			ctx.json(projectList);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}


    private static String selectStatement(String whereStatement) {
        return "SELECT " +
                "p.id, " +
                "p.slug, " +
                "p.modrinth_id, " +
                "p.attributed_to, " +
                "CASE " +
                    "WHEN a.user_id IS NOT NULL THEN group_concat(DISTINCT a.user_id)" +
                    "ELSE '' " +
                "END AS authors " +
                "FROM " +
                    "projects p " +
                "LEFT JOIN " +
                    "project_authors a ON p.id = a.project_id " +
                "WHERE " +
                    "p." + whereStatement + " " +
                "GROUP BY " +
                    "p.id, p.modrinth_id, p.attributed_to";
    }

	private static String selectAllByUser(String user) {
		return """
				 SELECT p.id,
				       p.slug,
				       p.modrinth_id,
				       p.attributed_to,
				       COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors
				FROM   projects p
				       LEFT JOIN project_authors a
				              ON p.id = a.project_id
				       LEFT JOIN users u
				              ON a.user_id = u.id
				WHERE  p.id IN (SELECT pa.project_id
				                FROM   project_authors pa
				                       JOIN users uu
				                         ON pa.user_id = uu.id
				                WHERE  uu.id = '%s'
				                        OR uu.username = '%s')
				GROUP  BY p.id,
				          p.slug,
				          p.modrinth_id,
				          p.attributed_to;
				""".formatted(user, user);
	}

	private static String selectAllByEvent(String event) {
		return """
			SELECT p.id,
				   p.slug,
				   p.modrinth_id,
				   p.attributed_to,
				   COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors
			FROM projects p
				LEFT JOIN project_authors a
					ON p.id = a.project_id
				LEFT JOIN users u
					ON a.user_id = u.id
				LEFT JOIN submissions s
					ON s.project_id = p.id
				LEFT JOIN events e
					ON e.id = s.event
			WHERE  e.id = '%s' or e.slug = '%s'
			GROUP  BY p.id,
					  p.slug,
					  p.modrinth_id,
					  p.attributed_to
			""".formatted(event, event);
	}

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
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
