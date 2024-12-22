package net.modgarden.backend.data.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.util.SQLiteOps;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public record User(String id,
                   String discordId,
                   Optional<String> modrinthId,
                   List<Project> projects,
                   List<Event> events,
                   List<MinecraftAccount.UserInstance> minecraftAccounts) {
    public static final Codec<User> FULL_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(User::projects),
            Event.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(User::events),
            MinecraftAccount.UserInstance.CODEC.listOf().optionalFieldOf("minecraft_accounts", List.of()).forGetter(User::minecraftAccounts)
    ).apply(inst, User::new)));
    public static final Codec<User> CODEC = Codec.STRING.xmap(User::queryFromId, User::id);

    public static void getUser(Context ctx) {
        String path = ctx.pathParam("user");
        String service = ctx.queryParam("service");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }

        String serviceEndString = switch (service) {
            case "modrinth" -> "Modrinth";
            case "discord" -> "Discord";
            case null, default -> "Mod Garden";
        };

        User user = query(path, service);
        if (user == null) {
            ModGardenBackend.LOG.error("Could not find user '{}'.", path);
            ctx.result("Could not find user '" + path + "' from service '" + serviceEndString + "'.");
            ctx.status(404);
            return;
        }
        ModGardenBackend.LOG.info("Successfully queried user from path '{}' from service '{}'.", path, serviceEndString);
        ctx.json(user);
    }

    @Nullable
    public static User query(String path,
                             @Nullable String service) {
        User user = null;

        if (service == null || "modgarden".equalsIgnoreCase(service))
            user = queryFromId(path);

        if ("modrinth".equalsIgnoreCase(service)) {
            user = queryFromModrinthUsername(path);
            if (user == null)
                user = queryFromModrinthId(path);
        }

        if ("discord".equalsIgnoreCase(service))
            user = queryFromDiscordId(path);

        return user;
    }

    private static User queryFromId(String id) {
        return innerQuery("id=?", id);
    }

    private static User queryFromDiscordId(String discordId) {
        return innerQuery("discord_id=?", discordId);
    }

    private static User queryFromModrinthId(String modrinthId) {
        return innerQuery("modrinth_id=?", modrinthId);
    }

    private static User queryFromModrinthUsername(String modrinthUsername) {
        try {
            String usernameToId = getUserModrinthId(modrinthUsername);
            if (usernameToId == null)
                return null;
            return queryFromModrinthId(usernameToId);
        } catch (IOException ex) {
            return null;
        }
    }

    private static User innerQuery(String whereStatement, String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement(whereStatement))) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result == null)
                return null;
            return FULL_CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Could not decode user. ", ex);
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }

    private static String getUserModrinthId(String modrinthUsername) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            ClassicHttpRequest request = ClassicRequestBuilder.get("https://api.modrinth.com/v2/user/" + modrinthUsername).build();
            return httpClient.execute(request, response -> {
                if (response.getCode() != 200)
                    return null;
                 InputStream stream = response.getEntity().getContent();
                 try (InputStreamReader reader = new InputStreamReader(stream)) {
                     JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                     return json.getAsJsonPrimitive("id").getAsString();
                 }
            });
        }
    }

    private static String selectStatement(String whereStatement) {
        return "SELECT\n" +
                "user.*," +
                "(" +
                "SELECT json_group_array(" +
                    "project.id " +
                ") " +
                "FROM projects project, json_each(project.authors)\n" +
                "WHERE json_each.value = user.id\n" +
                ") AS projects, " +
                "(" +
                "SELECT json_group_array(\n" +
                    "submission.event\n" +
                ")" +
                "FROM submissions submission\n" +
                "WHERE submission.project_id = project.id\n" +
                ") AS events," +
                "(" +
                "SELECT json_group_array(\n" +
                    "json_object(\n" +
                        "'uuid', mcacc.uuid,\n" +
                        "'verified', mcacc.verified\n" +
                    ")" +
                ") " +
                "FROM minecraft_accounts mcacc, json_each(mcacc.linked_to) " +
                "WHERE json_each.value = user.id" +
                ") AS minecraft_accounts " +
                "FROM users user " +
                "FULL JOIN projects project " +
                    "ON CASE " +
                        "WHEN authors NOT NULL " +
                        "THEN " +
                            "EXISTS (" +
                                "SELECT * " +
                                "FROM json_each(project.authors) " +
                                "WHERE json_each.value = user.id " +
                            ")" +
                        "ELSE FALSE " +
                    "END " +
                "WHERE user." + whereStatement;
    }
}
