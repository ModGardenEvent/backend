package net.modgarden.backend.handler.v1.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.util.ExtraCodecs;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

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

        try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
            if (body.service.equals(LinkCode.Service.MODRINTH.serializedName())) {
                try (var deleteStatement = connection.prepareStatement("UPDATE users SET modrinth_id = NULL WHERE discord_id = ?")) {
					deleteStatement.setString(1, body.discordId);
					int resultSet = deleteStatement.executeUpdate();

					if (resultSet == 0) {
						ctx.result("Mod Garden account associated with Discord ID '" + body.discordId + "' does not have a Modrinth account linked.");
						ctx.status(200);
					}

					ctx.result("Successfully unlinked Modrinth account from Mod Garden account associated with Discord ID '" + body.discordId + "'.");
					ctx.status(201);
                }
				return;
            }
			if (body.service.equals(LinkCode.Service.MINECRAFT.serializedName())) {
				if (body.minecraftUuid.isEmpty()) {
					ctx.result("'minecraft_uuid' field was not specified.");
					ctx.status(400);
					return;
				}

				try (var deleteStatement = connection.prepareStatement("DELETE FROM minecraft_accounts WHERE uuid = ?")) {
					deleteStatement.setString(1, body.minecraftUuid.get().toString().replace("-", ""));
					int resultSet = deleteStatement.executeUpdate();

					if (resultSet == 0) {
						ctx.result("Mod Garden account associated with Discord ID '" + body.discordId + "' does not have the specified Minecraft account linked to it.");
						ctx.status(200);
						return;
					}

					ctx.result("Successfully unlinked Minecraft account " + body.minecraftUuid.get() + " from Mod Garden account associated with Discord ID '" + body.discordId + "'.");
					ctx.status(201);
				}
			}
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
    }

	public record Body(String discordId, String service, Optional<UUID> minecraftUuid) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(Body::discordId),
				Codec.STRING.fieldOf("service").forGetter(Body::service),
				ExtraCodecs.UUID_CODEC.optionalFieldOf("minecraft_uuid").forGetter(Body::minecraftUuid)
		).apply(inst, Body::new));
	}
}
