package net.modgarden.backend.handler.v1;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.oauth.OAuthService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class RegistrationHandler {
    public static void discordBotRegister(Context ctx) {
        if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
            ctx.result("Unauthorized.");
            ctx.status(401);
            return;
        }

		if (!("application/json").equals(ctx.header("Content-Type"))) {
			ctx.result("Invalid Content-Type.");
			ctx.status(415);
			return;
		}

		Body body = ctx.bodyAsClass(Body.class);
		String username = body.username.orElse(null);
		String displayName = body.displayName.orElse(null);

        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkDiscordIdStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ?");
			 var checkUsernameStatement = connection.prepareStatement("SELECT 1 FROM users WHERE username = ?");
             var insertStatement = connection.prepareStatement("INSERT INTO users(id, username, display_name, discord_id, created, permissions) VALUES (?, ?, ?, ?, ?, ?)")) {
			checkDiscordIdStatement.setString(1, body.id);
            ResultSet existingDiscordUser = checkDiscordIdStatement.executeQuery();
            if (existingDiscordUser != null && existingDiscordUser.getBoolean(1)) {
                ctx.result("Discord user is already registered.");
                ctx.status(422);
                return;
            }

            if (username == null || displayName == null) {
                var discordClient = OAuthService.DISCORD.authenticate();
                try (var stream = discordClient.get("users/" + body.id, HttpResponse.BodyHandlers.ofInputStream()).body();
                     var reader = new InputStreamReader(stream)) {
                    var json = JsonParser.parseReader(reader);
                    if (username == null)
                        username = json.getAsJsonObject().get("username").getAsString();
                    if (displayName == null)
                        displayName = json.getAsJsonObject().get("global_name").getAsString();
                }
            }

			if (username == null) {
				ctx.result("Could not resolve username.");
				ctx.status(500);
				return;
			} else if (username.length() > 32) {
				ctx.result("Username is too long.");
				ctx.status(422);
				return;
			} else if (!username.matches(User.USERNAME_REGEX)) {
				ctx.result("Username has invalid characters.");
				ctx.status(422);
				return;
			}
			if (displayName == null) {
				ctx.result("Could not resolve display name.");
				ctx.status(500);
				return;
			} else if (displayName.length() > 32) {
				ctx.result("Display name is too long.");
				ctx.status(422);
				return;
			}

			checkUsernameStatement.setString(1, username);
			ResultSet existingUsername = checkDiscordIdStatement.executeQuery();
			if (existingUsername != null && existingUsername.getBoolean(1)) {
				ctx.result("Username '" + username + "' has been taken.");
				ctx.status(422);
				return;
			}

			long id = User.ID_GENERATOR.next();

            insertStatement.setString(1, Long.toString(id));
            insertStatement.setString(2, username);
            insertStatement.setString(3, displayName);
            insertStatement.setString(4, body.id);
            insertStatement.setLong(5, System.currentTimeMillis());
			insertStatement.setLong(6, 0);
            insertStatement.execute();
        } catch (SQLException | IOException | InterruptedException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
            return;
        }

        ctx.result("Successfully registered Mod Garden account.");
        ctx.status(201);
    }


	public record Body(String id, Optional<String> username, Optional<String> displayName) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("id").forGetter(Body::id),
				Codec.STRING.optionalFieldOf("username").forGetter(Body::username),
				Codec.STRING.optionalFieldOf("display_name").forGetter(Body::displayName)
		).apply(inst, Body::new));
	}
}
