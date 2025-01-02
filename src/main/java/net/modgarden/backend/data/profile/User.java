package net.modgarden.backend.data.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record User(String id,
                   String discordId,
                   Optional<String> modrinthId,
                   List<String> projects,
                   List<String> events,
                   List<UUID> minecraftAccounts,
                   List<AwardInstance.UserValues> awards) {
    public static final Codec<User> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            Project.ID_CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(User::projects),
            Event.ID_CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(User::events),
            ExtraCodecs.UUID_CODEC.listOf().optionalFieldOf("minecraft_accounts", List.of()).forGetter(User::minecraftAccounts),
            AwardInstance.UserValues.CODEC.listOf().optionalFieldOf("awards", List.of()).forGetter(User::awards)
    ).apply(inst, User::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(User::validate);

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
        User user;

        if ("modrinth".equalsIgnoreCase(service)) {
            user = queryFromModrinthUsername(path.toLowerCase(Locale.ROOT));
            if (user == null)
                user = queryFromModrinthId(path);
            return user;
        }

        if ("discord".equalsIgnoreCase(service)) {
            user = queryFromDiscordUsername(path.toLowerCase(Locale.ROOT));
            if (user == null)
                user = queryFromDiscordId(path);
            return user;
        }

        return queryFromId(path);
    }

    private static User queryFromId(String id) {
        return innerQuery("id = ?", id);
    }

    private static User queryFromDiscordId(String discordId) {
        return innerQuery("discord_id = ?", discordId);
    }

    private static User queryFromModrinthId(String modrinthId) {
        return innerQuery("modrinth_id = ?", modrinthId);
    }

    private static User queryFromDiscordUsername(String discordUsername) {
        try {
            String usernameToId = getUserDiscordId(discordUsername);
            if (usernameToId == null)
                return null;
            return queryFromDiscordId(usernameToId);
        } catch (IOException | InterruptedException ex) {
            return null;
        }
    }

    private static User queryFromModrinthUsername(String modrinthUsername) {
        try {
            String usernameToId = getUserModrinthId(modrinthUsername);
            if (usernameToId == null)
                return null;
            return queryFromModrinthId(usernameToId);
        } catch (IOException | InterruptedException ex) {
            return null;
        }
    }

    private static User innerQuery(String whereStatement, String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement(selectStatement(whereStatement))) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Could not decode user. ", ex);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result != null && result.getBoolean(1))
                return DataResult.success(id);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return DataResult.error(() -> "Failed to get user with id '" + id + "'.");
    }

    private static String getUserModrinthId(String modrinthUsername) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/user/" + modrinthUsername))
                .build();
        HttpResponse<InputStream> stream = ModGardenBackend.HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (stream.statusCode() != 200)
            return null;
        try (InputStreamReader reader = new InputStreamReader(stream.body())) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject())
                return null;
            return element.getAsJsonObject().getAsJsonPrimitive("id").getAsString();
        }
    }

    private static String getUserDiscordId(String discordUsername) throws IOException, InterruptedException {
        var client = OAuthService.DISCORD.authenticate();
        var stream = client.getResponse("guilds/1266288344644452363/members/search?query=" + discordUsername, HttpResponse.BodyHandlers.ofInputStream());
        if (stream.statusCode() != 200)
            return null;
        try (InputStreamReader reader = new InputStreamReader(stream.body())) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonArray() || element.getAsJsonArray().isEmpty() || !element.getAsJsonArray().get(0).isJsonObject())
                return null;
            return element.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("user").getAsJsonPrimitive("id").getAsString();
        }
    }

    private static String selectStatement(String whereStatement) {
        return "SELECT " +
                    "u.id, " +
                    "u.discord_id, " +
                    "u.modrinth_id, " +
                    "CASE " +
                        "WHEN p.id NOT NULL THEN json_group_array(DISTINCT p.id) " +
                        "ELSE json_array() " +
                    "END AS projects, " +
                    "CASE " +
                        "WHEN e.id NOT NULL THEN json_group_array(DISTINCT e.id) " +
                        "ELSE json_array() " +
                    "END AS events, " +
                    "CASE " +
                        "WHEN ma.uuid NOT NULL THEN json_group_array(DISTINCT ma.uuid) " +
                        "ELSE json_array() " +
                    "END AS minecraft_accounts, " +
                    "CASE " +
                        "WHEN ai.award_id NOT NULL THEN json_group_array(DISTINCT json_object('award_id', ai.award_id, 'additional_tooltip', ai.additional_tooltip)) " +
                        "ELSE json_array() " +
                    "END AS awards " +
                "FROM " +
                    "users u " +
                "LEFT JOIN " +
                    "projects p, project_authors a ON u.id = a.user_id AND p.id = a.project_id " +
                "LEFT JOIN " +
                    "submissions s ON p.id = s.project_id " +
                "LEFT JOIN " +
                    "events e ON s.event = e.id " +
                "LEFT JOIN " +
                    "minecraft_accounts ma ON u.id = ma.user_id " +
                "LEFT JOIN " +
                    "award_instances ai ON u.id = ai.awarded_to " +
                "WHERE " +
                    "u." + whereStatement + " " +
                "GROUP BY " +
                    "u.id, u.discord_id, u.modrinth_id";
    }
}
