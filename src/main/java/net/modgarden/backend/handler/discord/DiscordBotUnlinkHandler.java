package net.modgarden.backend.handler.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public class DiscordBotUnlinkHandler {
    public static void unlink(Context ctx) {
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

        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            if (body.service.equals(LinkCode.Service.MODRINTH.serializedName())) {
                try (var deleteStatement = connection.prepareStatement("UPDATE users SET modrinth_id = NULL WHERE discord_id = ?")) {
					deleteStatement.setString(1, body.discordId);
					int resultSet = deleteStatement.executeUpdate();

					if (resultSet == 0) {
						ctx.result("Mod Garden account associated with Discord ID '" + body.discordId + "' does not have a " + capitalisedService + " account linked.");
						ctx.status(200);
					}

					ctx.result("Successfully unlinked " + capitalisedService + " account from Mod Garden account associated with Discord ID '" + body.discordId + "'.");
					ctx.status(201);
                }
            }
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
    }

	public record Body(String discordId, String service) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(Body::discordId),
				Codec.STRING.fieldOf("service").forGetter(Body::service)
		).apply(inst, Body::new));
	}
}
