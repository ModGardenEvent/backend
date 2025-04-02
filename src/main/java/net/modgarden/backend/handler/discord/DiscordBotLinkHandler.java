package net.modgarden.backend.handler.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class DiscordBotLinkHandler {
    public static void link(Context ctx) {
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

        String capitalisedService = body.service.substring(0, 1).toUpperCase(Locale.ROOT) + body.service.substring(1);

        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkStatement = connection.prepareStatement("SELECT account_id FROM link_codes WHERE code = ? AND service = ?");
             var deleteStatement = connection.prepareStatement("DELETE FROM link_codes WHERE code = ? AND service = ?")) {
            checkStatement.setString(1, body.linkCode);
            checkStatement.setString(2, body.service);
            ResultSet checkResult = checkStatement.executeQuery();
            String accountId = checkResult.getString(1);

            deleteStatement.setString(1, body.linkCode);
			checkStatement.setString(2, body.service);
            deleteStatement.execute();
            if (accountId == null) {
                ctx.result("Invalid link code for " + capitalisedService + ".");
                ctx.status(500);
                return;
            }

            if (body.service.equals(LinkCode.Service.MODRINTH.serializedName())) {
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

                    userCheckStatement.setString(1, body.discordId);
                    ResultSet userCheckResult = userCheckStatement.executeQuery();
                    if (userCheckResult.isBeforeFirst() && userCheckResult.getBoolean(1)) {
                        ctx.result("The specified Mod Garden account is already linked with " + capitalisedService + ".");
                        ctx.status(422);
                        return;
                    }

                    insertStatement.setString(1, accountId);
                    insertStatement.setString(2, body.discordId);
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

	public record Body(String discordId, String linkCode, String service) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(Body::discordId),
				Codec.STRING.fieldOf("link_code").forGetter(Body::linkCode),
				Codec.STRING.fieldOf("service").forGetter(Body::service)
		).apply(inst, Body::new));
	}
}
