package net.modgarden.backend.data.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public record Project(String id,
                      String attributedTo,
                      List<User> authors,
                      List<Event> events) {
    public static final Codec<Project> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("attributed_to").forGetter(Project::attributedTo),
            User.CODEC.listOf().optionalFieldOf("authors", List.of()).forGetter(Project::authors),
            Event.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Project::events)
    ).apply(inst, Project::new)));
    public static final Codec<Project> CODEC = Codec.STRING.xmap(Project::query, Project::id);

    public static void getProject(Context ctx) {
        String path = ctx.pathParam("project");
        Project project = query(path);
        if (project == null) {
            ModGardenBackend.LOG.error("Could not find project '{}'.", path);
            ctx.result("Could not find project '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, project).getOrThrow().toString(), Event.class));
    }

    public static Project query(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM projects WHERE id=?")) {
            prepared.setString(1, id);
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, prepared.executeQuery()).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode project from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            throw new NullPointerException("Could not find project inside project database.");
        }
    }
}
