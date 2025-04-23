package net.modgarden.backend.data.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public record User(String id,
                   String username,
                   String displayName,
				   Optional<String> avatarUrl,
				   Optional<String> pronouns,
                   String discordId,
                   Optional<String> modrinthId,
                   ZonedDateTime created,
                   List<String> projects,
                   List<String> events,
                   List<UUID> minecraftAccounts,
                   List<AwardInstance.UserValues> awards,
				   List<Permission> permissions) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(0);

	public static final String USERNAME_REGEX = "^(?=.{2,32}$).?[a-z0-9_]+(?:.[a-z0-9_]+)*.?$";
	public static final String DISPLAY_NAME_REGEX = "^(?=.{2,32}$)[A-Za-z]+(( )?(('|-|.)?([A-Za-z])+))*$";

    public static final Codec<User> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("username").forGetter(User::username),
            Codec.STRING.fieldOf("display_name").forGetter(User::displayName),
			Codec.STRING.optionalFieldOf("avatar_url").forGetter(User::avatarUrl),
			Codec.STRING.optionalFieldOf("pronouns").forGetter(User::pronouns),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            ExtraCodecs.ISO_DATE_TIME.fieldOf("created").forGetter(User::created),
            Project.ID_CODEC.listOf().fieldOf("projects").forGetter(User::projects),
            Event.ID_CODEC.listOf().fieldOf("events").forGetter(User::events),
            ExtraCodecs.UUID_CODEC.listOf().fieldOf("minecraft_accounts").forGetter(User::minecraftAccounts),
            AwardInstance.UserValues.CODEC.listOf().fieldOf("awards").forGetter(User::awards),
			Permission.LIST_CODEC.fieldOf("permissions").forGetter(User::permissions)
    ).apply(inst, User::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(User::validate);
	public static final Codec<User> CODEC = ID_CODEC.xmap(User::queryFromId, user -> user.id);

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
            ModGardenBackend.LOG.debug("Could not find user '{}'.", path);
            ctx.result("Could not find user '" + path + "' from service '" + serviceEndString + "'.");
            ctx.status(404);
            return;
        }
        ModGardenBackend.LOG.debug("Successfully queried user from path '{}' from service '{}'.", path, serviceEndString);
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

        user = queryFromUsername(path);
        if (user == null)
            user = queryFromId(path);
        return user;
    }

    private static User queryFromUsername(String username) {
        return innerQuery("username = ?", username);
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

			var projectJson = result.getString("projects"); // Array of strings
			var eventJson = result.getString("events"); // Array of strings
			var minecraftAccountJson = result.getString("minecraft_accounts"); // Array of UUIDs
			var awardJson = result.getString("awards"); // Array of award instance user values

			List<String> projects = result.getString("projects").isEmpty() ? List.of() : List.of(projectJson.split(","));
			List<String> events = result.getString("events").isEmpty() ? List.of() : List.of(eventJson.split(","));

			List<UUID> minecraftAccounts = ExtraCodecs.UUID_CODEC.listOf().decode(JsonOps.INSTANCE, JsonParser.parseString(minecraftAccountJson)).getOrThrow().getFirst();
			List<AwardInstance.UserValues> awards = AwardInstance.UserValues.CODEC.listOf().decode(JsonOps.INSTANCE, JsonParser.parseString(awardJson)).getOrThrow().getFirst();

			return new User(
					result.getString("id"),
					result.getString("username"),
					result.getString("display_name"),
					Optional.ofNullable(result.getString("avatar_url")),
					Optional.ofNullable(result.getString("pronouns")),
					result.getString("discord_id"),
					Optional.ofNullable(result.getString("modrinth_id")),
					ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getLong("created")), ZoneId.of("GMT")),
					projects,
					events,
					minecraftAccounts,
					awards,
					Permission.fromLong(result.getLong("permissions"))
			);
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
        var modrinthClient = OAuthService.MODRINTH.authenticate();
        var stream = modrinthClient.get("v2/user/" + modrinthUsername, HttpResponse.BodyHandlers.ofInputStream());
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
        var discordClient = OAuthService.DISCORD.authenticate();
        var stream = discordClient.get("guilds/1266288344644452363/members/search?query=" + discordUsername, HttpResponse.BodyHandlers.ofInputStream());
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
                    "u.username, " +
                    "u.display_name, " +
					"u.avatar_url, " +
					"u.pronouns, " +
                    "u.discord_id, " +
                    "u.modrinth_id, " +
                    "u.created, " +
					"u.permissions, " +
                    "CASE " +
                        "WHEN p.id NOT NULL THEN group_concat(DISTINCT p.id) " +
                        "ELSE '' " +
                    "END AS projects, " +
                    "CASE " +
                        "WHEN e.id NOT NULL THEN group_concat(DISTINCT e.id) " +
                        "ELSE '' " +
                    "END AS events, " +
                    "CASE " +
                        "WHEN ma.uuid NOT NULL THEN json_group_array(DISTINCT ma.uuid) " +
                        "ELSE json_array() " +
                    "END AS minecraft_accounts, " +
                    "CASE " +
                        "WHEN ai.award_id NOT NULL THEN json_group_array(DISTINCT json_object('award_id', ai.award_id, 'custom_data', ai.custom_data)) " +
                        "ELSE json_array() " +
                    "END AS awards " +
                "FROM " +
                    "users u " +
                "LEFT JOIN " +
                    "project_authors a ON u.id = a.user_id " +
                "LEFT JOIN " +
                    "projects p ON p.id = a.project_id " +
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
                    "u.id, u.username, u.display_name, u.discord_id, u.modrinth_id, u.created, u.permissions";
    }
}
