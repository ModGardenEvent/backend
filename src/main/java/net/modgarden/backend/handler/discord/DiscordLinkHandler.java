package net.modgarden.backend.handler.discord;

import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class DiscordLinkHandler {
    public static void link(Context ctx) {
        if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
            ctx.result("Unauthorized.");
            ctx.status(401);
            return;
        }

        String discordId = ctx.queryParam("id");
        String linkCode = ctx.queryParam("linkcode");
        String service = ctx.queryParam("service");

        if (discordId == null) {
            ctx.result("Could not get Discord ID parameter.");
            ctx.status(404);
            return;
        }
        if (linkCode == null) {
            ctx.result("Could not get link code parameter.");
            ctx.status(404);
            return;
        }
        if (service == null) {
            ctx.result("Could not get service parameter.");
            ctx.status(404);
            return;
        }

        String capitalisedService = service.substring(0, 1).toUpperCase(Locale.ROOT) + service.substring(1);

        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkStatement = connection.prepareStatement("SELECT account_id FROM link_codes WHERE code = ? AND service = ?");
             var deleteStatement = connection.prepareStatement("DELETE FROM link_codes WHERE code = ? AND service = ?")) {
            checkStatement.setString(1, linkCode);
            checkStatement.setString(2, service);
            ResultSet checkResult = checkStatement.executeQuery();
            String accountId = checkResult.getString(1);

            deleteStatement.setString(1, linkCode);
            deleteStatement.setString(2, service);
            deleteStatement.execute();
            if (accountId == null) {
                ctx.result("Invalid link code for " + capitalisedService + ".");
                ctx.status(500);
                return;
            }

            if (service.equals(LinkCode.Service.MODRINTH.serializedName())) {
                try (var modrinthCheckStatement = connection.prepareStatement("SELECT 1 FROM users WHERE modrinth_id = ?");
                     var userCheckStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ? AND modrinth_id IS NOT NULL");
                     var insertStatement = connection.prepareStatement("UPDATE users SET modrinth_id = ? WHERE discord_id = ?")) {
                    modrinthCheckStatement.setString(1, accountId);
                    ResultSet modrinthCheckResult = modrinthCheckStatement.executeQuery();
                    if (modrinthCheckResult.isBeforeFirst() && modrinthCheckResult.getBoolean(1)) {
                        ctx.result("The specified " + capitalisedService + " account has already been linked to a Mod Garden account.");
                        ctx.status(422);
                        return;
                    }

                    userCheckStatement.setString(1, discordId);
                    ResultSet userCheckResult = userCheckStatement.executeQuery();
                    if (userCheckResult.isBeforeFirst() && userCheckResult.getBoolean(1)) {
                        ctx.result("The specified Mod Garden account is already linked with " + capitalisedService + ".");
                        ctx.status(422);
                        return;
                    }

                    insertStatement.setString(1, accountId);
                    insertStatement.setString(2, discordId);
                    insertStatement.execute();

                    ctx.result("Successfully linked " + capitalisedService + " account to Mod Garden.");
                    ctx.status(201);
                }
            }
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
    }
}
