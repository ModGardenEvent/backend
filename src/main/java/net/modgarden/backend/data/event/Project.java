package net.modgarden.backend.data.event;

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

        ModGardenBackend.LOG.info("Successfully queried project from path '{}'", path);
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
