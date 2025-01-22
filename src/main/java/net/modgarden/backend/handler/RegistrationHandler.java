package net.modgarden.backend.handler;

import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RegistrationHandler {
    public static void registerThroughDiscord(Context ctx) {
        // TODO: Unhardcode this from the Discord Bot when the web frontend is available.
        if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
            ctx.result("Unauthorized.");
            ctx.status(401);
            return;
        }

        String discordId = ctx.queryParam("id");
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ?");
             var insertStatement = connection.prepareStatement("INSERT INTO users(id, discord_id, creation_time) VALUES (?, ?, ?)")) {
            checkStatement.setString(1, discordId);
            ResultSet result = checkStatement.executeQuery();
            if (result != null && result.getBoolean(1)) {
                ctx.result("Discord user is already registered.");
                ctx.status(200);
                return;
            }
            // This is likely going to be the only snowflake generator across this project.
            // So we can use generatorId 0.
            int generatorId = 0;
            SnowflakeIdGenerator generator = SnowflakeIdGenerator.createDefault(generatorId);
            long id = generator.next();
            insertStatement.setString(1, Long.toString(id));
            insertStatement.setString(2, discordId);
            insertStatement.setLong(3, System.currentTimeMillis());
            insertStatement.execute();
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
            return;
        }

        ctx.result("Successfully registered Mod Garden account.");
        ctx.status(201);
    }
}
