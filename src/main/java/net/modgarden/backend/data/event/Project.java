package net.modgarden.backend.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

// TODO: Allow creating organisations, allow projects to be attributed to an organisation.
// TODO: Potentially allow GitHub only projects. Not necessarily now, but more notes on this will be placed in internal team chats. - Calico
public record Project(String id,
                      String slug,
                      String modrinthId,
					  String attributedTo,
                      List<String> authors,
					  List<String> builders) {
	public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("slug").forGetter(Project::slug),
            Codec.STRING.fieldOf("modrinth_id").forGetter(Project::modrinthId),
            User.ID_CODEC.fieldOf("attributed_to").forGetter(Project::attributedTo),
            User.ID_CODEC.listOf().fieldOf("authors").forGetter(Project::authors),
			User.ID_CODEC.listOf().fieldOf("builders").forGetter(Project::builders)
    ).apply(inst, Project::new)));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Project::validate);
	public static final Codec<Project> CODEC = ID_CODEC.xmap(Project::queryFromId, Project::id);

    public static void getProject(Context ctx) {
        String path = ctx.pathParam("project");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        // TODO: Allow Modrinth as a service.
        Project project = queryFromSlug(path);
		if (project == null) {
			project = queryFromId(path);
		}
        if (project == null) {
            ModGardenBackend.LOG.debug("Could not find project '{}'.", path);
            ctx.result("Could not find project '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.debug("Successfully queried project from path '{}'", path);
        ctx.json(project);
    }

	public static Project queryFromSlug(String slug) {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement(selectBySlug())) {
			prepared.setString(1, slug);
			ResultSet result = prepared.executeQuery();
			if (!result.isBeforeFirst())
				return null;
			List<String> authors = Arrays.stream(result.getString("authors").split(","))
					.filter(s -> !s.isBlank())
					.toList();
			List<String> builders = Arrays.stream(result.getString("builders").split(","))
					.filter(s -> !s.isBlank())
					.toList();
			return new Project(
					result.getString("id"),
					result.getString("slug"),
					result.getString("modrinth_id"),
					result.getString("attributed_to"),
					authors,
					builders
			);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return null;
	}

    public static Project queryFromId(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectById())) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			List<String> authors = Arrays.stream(result.getString("authors").split(","))
					.filter(s -> !s.isBlank())
					.toList();
			List<String> builders = Arrays.stream(result.getString("builders").split(","))
					.filter(s -> !s.isBlank())
					.toList();
			return new Project(
					result.getString("id"),
					result.getString("slug"),
					result.getString("modrinth_id"),
					result.getString("attributed_to"),
					authors,
					builders
			);
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
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement(selectAllByUser())) {
			prepared.setString(1, user);
			prepared.setString(2, user);
			ResultSet result = prepared.executeQuery();
			var projectList = new JsonArray();
			while (result.next()) {
				var projectObject = new JsonObject();
				var authors = new JsonArray();
				var builders = new JsonArray();

				for (String author : result.getString("authors").split(",")) {
					authors.add(author);
				}
				for (String builder : result.getString("builders").split(",")) {
					builders.add(builder);
				}
				projectObject.addProperty("id", result.getString("id"));
				projectObject.addProperty("slug", result.getString("slug"));
				projectObject.addProperty("modrinth_id", result.getString("modrinth_id"));
				projectObject.addProperty("attributed_to", result.getString("attributed_to"));
				projectObject.add("authors", authors);
				projectObject.add("builders", builders);
				projectList.add(projectObject);
			}
			ctx.json(projectList);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	private static String selectById() {
		return """
				SELECT
					p.id,
					p.slug,
					p.modrinth_id,
					p.attributed_to,
				 	COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors,
					COALESCE(Group_concat(DISTINCT b.user_id), '') AS builders
				FROM projects p
					LEFT JOIN project_authors a
				    	ON p.id = a.project_id
						LEFT JOIN project_builders b
							ON p.id = b.project_id
				WHERE
					p.id = ?
				GROUP BY
					p.id,
					p.slug,
					p.modrinth_id,
					p.attributed_to
				""";
	}

	private static String selectBySlug() {
		return """
				SELECT
					p.id,
					p.slug,
					p.modrinth_id,
					p.attributed_to,
				 	COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors,
					COALESCE(Group_concat(DISTINCT b.user_id), '') AS builders
				FROM projects p
					LEFT JOIN project_authors a
				    	ON p.id = a.project_id
						LEFT JOIN project_builders b
							ON p.id = b.project_id
				WHERE
					p.slug = ?
				GROUP BY
					p.id,
					p.slug,
					p.modrinth_id,
					p.attributed_to
				""";
	}

	private static String selectAllByUser() {
		return """
				SELECT p.id,
				 	p.slug,
				 	p.modrinth_id,
				 	p.attributed_to,
				 	COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors,
					COALESCE(Group_concat(DISTINCT b.user_id), '') AS builders
				FROM projects p
					LEFT JOIN project_authors a
				    	ON p.id = a.project_id
						LEFT JOIN project_builders b
							ON p.id = b.project_id
						LEFT JOIN users u
							ON a.user_id = u.id
				WHERE p.id IN (SELECT pa.project_id
					FROM project_authors pa
						JOIN users uu
							ON pa.user_id = uu.id
						WHERE uu.id = ?
							OR uu.username = ?)
				GROUP BY p.id,
					p.slug,
					p.modrinth_id,
					p.attributed_to
				""";
	}

	private static String selectAllByEvent() {
		return """
			SELECT p.id,
				p.slug,
				p.modrinth_id,
				p.attributed_to,
				COALESCE(Group_concat(DISTINCT a.user_id), '') AS authors,
				COALESCE(Group_concat(DISTINCT b.user_id), '') AS builders
			FROM projects p
				LEFT JOIN project_authors a
					ON p.id = a.project_id
				LEFT JOIN project_builders b
					ON p.id = b.project_id
				LEFT JOIN users u
					ON a.user_id = u.id
				LEFT JOIN submissions s
					ON s.project_id = p.id
				LEFT JOIN events e
					ON e.id = s.event
			WHERE e.id = ? or e.slug = ?
			GROUP BY p.id,
				p.slug,
				p.modrinth_id,
				p.attributed_to
			""";
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
