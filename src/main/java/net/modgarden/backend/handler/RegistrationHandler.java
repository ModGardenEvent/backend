package net.modgarden.backend.handler;

import com.google.gson.JsonParser;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
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

public class RegistrationHandler {
    public static void registerThroughDiscordBot(Context ctx) {
        if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
            ctx.result("Unauthorized.");
            ctx.status(401);
            return;
        }

        String discordId = ctx.queryParam("id");
        String username = ctx.queryParam("username");
        String displayName = ctx.queryParam("displayname");
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ?");
             var insertStatement = connection.prepareStatement("INSERT INTO users(id, username, display_name, discord_id, created) VALUES (?, ?, ?, ?, ?)")) {
            checkStatement.setString(1, discordId);
            ResultSet result = checkStatement.executeQuery();
            if (result != null && result.getBoolean(1)) {
                ctx.result("Discord user is already registered.");
                ctx.status(200);
                return;
            }
            long id = User.ID_GENERATOR.next();

            if (username == null || displayName == null) {
                var discordClient = OAuthService.DISCORD.authenticate();
                try (var stream = discordClient.get("users/" + discordId, HttpResponse.BodyHandlers.ofInputStream()).body();
                     var reader = new InputStreamReader(stream)) {
                    var json = JsonParser.parseReader(reader);
                    if (username == null)
                        username = json.getAsJsonObject().get("username").getAsString();
                    if (displayName == null)
                        displayName = json.getAsJsonObject().get("global_name").getAsString();
                }
            }

            insertStatement.setString(1, Long.toString(id));
            insertStatement.setString(2, username);
            insertStatement.setString(3, displayName);
            insertStatement.setString(4, discordId);
            insertStatement.setLong(5, System.currentTimeMillis());
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
}
