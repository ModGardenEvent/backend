package net.modgarden.backend.data.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.util.DatabaseAccess;
import net.modgarden.backend.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
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
                   String username,
                   String displayName,
                   String discordId,
                   Optional<String> modrinthId,
                   long created,
                   List<String> projects,
                   List<String> events,
                   List<UUID> minecraftAccounts,
                   List<AwardInstance.UserValues> awards) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(0);
    public static final Codec<User> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("username").forGetter(User::username),
            Codec.STRING.fieldOf("display_name").forGetter(User::displayName),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            Codec.LONG.fieldOf("created").forGetter(User::created),
            Project.ID_CODEC.listOf().fieldOf("projects").forGetter(User::projects),
            Event.ID_CODEC.listOf().fieldOf("events").forGetter(User::events),
            ExtraCodecs.UUID_CODEC.listOf().fieldOf("minecraft_accounts").forGetter(User::minecraftAccounts),
            AwardInstance.UserValues.CODEC.listOf().fieldOf("awards").forGetter(User::awards)
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
			user = DatabaseAccess.userFromModrinthUsername(path.toLowerCase(Locale.ROOT));
			if (user == null)
				user = DatabaseAccess.userFromModrinthId(path);
			return user;
		}

		if ("discord".equalsIgnoreCase(service)) {
			user = DatabaseAccess.userFromDiscordUsername(path.toLowerCase(Locale.ROOT));
			if (user == null)
				user = DatabaseAccess.userFromDiscordId(path);
			return user;
		}

		user = DatabaseAccess.userFromUsername(path);
		if (user == null)
			user = DatabaseAccess.userFromId(path);
		return user;

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

    public static String getUserModrinthId(String modrinthUsername) throws IOException, InterruptedException {
        var modrinth = OAuthService.MODRINTH.authenticate();
        var stream = modrinth.get("v2/user/" + modrinthUsername, HttpResponse.BodyHandlers.ofInputStream());
        if (stream.statusCode() != 200)
            return null;
        try (InputStreamReader reader = new InputStreamReader(stream.body())) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject())
                return null;
            return element.getAsJsonObject().getAsJsonPrimitive("id").getAsString();
        }
    }

    public static String getUserDiscordId(String discordUsername) throws IOException, InterruptedException {
        var client = OAuthService.DISCORD.authenticate();
        var stream = client.get("guilds/1266288344644452363/members/search?query=" + discordUsername, HttpResponse.BodyHandlers.ofInputStream());
        if (stream.statusCode() != 200)
            return null;
        try (InputStreamReader reader = new InputStreamReader(stream.body())) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonArray() || element.getAsJsonArray().isEmpty() || !element.getAsJsonArray().get(0).isJsonObject())
                return null;
            return element.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("user").getAsJsonPrimitive("id").getAsString();
        }
    }
}
