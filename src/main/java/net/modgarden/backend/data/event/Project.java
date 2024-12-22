package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public record Project(String id,
                      String modrinthId,
                      String attributedTo,
                      List<User> authors) {
    public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("modrinth_id").forGetter(Project::modrinthId),
            Codec.STRING.fieldOf("attributed_to").forGetter(Project::attributedTo),
            User.CODEC.listOf().optionalFieldOf("authors", List.of()).forGetter(Project::authors)
    ).apply(inst, Project::new)));
    public static final Codec<Project> CODEC = Codec.STRING.xmap(Project::innerQuery, Project::id);

    public static void getProject(Context ctx) {
        String path = ctx.pathParam("project");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        // TODO: Allow Modrinth as a service.
        Project project = innerQuery(path.toLowerCase(Locale.ROOT));
        if (project == null) {
            ModGardenBackend.LOG.error("Could not find project '{}'.", path);
            ctx.result("Could not find project '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(project);
    }

    private static Project innerQuery(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM projects WHERE id=?")) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result == null)
                return null;
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode project from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }
}
