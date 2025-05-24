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
	    if (ModGardenBackend.isUnauthorized(ctx)) return;

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
				return;
            }
			if (body.service.equals(LinkCode.Service.MINECRAFT.serializedName())) {
				if (body.minecraftUuid.isEmpty()) {
					ctx.result("'minecraft_uuid' field was not specified.");
					ctx.status(422);
					return;
				}

				try (var insertStatement = connection.prepareStatement("UPDATE users SET minecraft_accounts = ? WHERE discord_id = ?");
					 var deleteStatement = connection.prepareStatement("UPDATE users SET minecraft_accounts = NULL WHERE discord_id = ?")) {
					User user = User.query(body.discordId, "discord");
					if (user == null) {
						ctx.result("Could not find user from Discord ID '" + body.discordId + "'.");
						ctx.status(422);
						return;
					}

					List<UUID> uuids = new ArrayList<>(user.minecraftAccounts());
					if (!uuids.contains(body.minecraftUuid.get())) {
						ctx.result("Minecraft account " + body.minecraftUuid.get() + " is not linked with user '" + user.username() + "'.");
						ctx.status(200);
						return;
					}
					uuids.remove(body.minecraftUuid.get());

					if (uuids.isEmpty()) {
						deleteStatement.setString(1, body.discordId);
						ctx.result("Successfully unlinked " + capitalisedService + " account " + body.minecraftUuid.get() + " from Mod Garden account associated with Discord ID '" + body.discordId + "'.");
						ctx.status(201);
						return;
					}

					var dataResult = ExtraCodecs.UUID_CODEC.listOf().encodeStart(JsonOps.INSTANCE, uuids);
					if (!dataResult.hasResultOrPartial()) {
						ModGardenBackend.LOG.error("Failed to create Minecraft account data. {}", dataResult.error().orElseThrow().message());
						ctx.result("Failed to create Minecraft account data.");
						ctx.status(500);
						return;
					}

					insertStatement.setString(1, dataResult.getOrThrow().toString());
					insertStatement.setString(2, body.discordId);
					insertStatement.execute();

					ctx.result("Successfully unlinked " + capitalisedService + " account " + body.minecraftUuid.get() + " from Mod Garden account associated with Discord ID '" + body.discordId + "'.");
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
