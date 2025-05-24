package net.modgarden.backend.handler.v1.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.util.ExtraCodecs;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DiscordBotLinkHandler {
    public static void link(Context ctx) {
	    if (ModGardenBackend.isUnauthorized(ctx)) return;

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
			deleteStatement.setString(2, body.service);
            deleteStatement.execute();
            if (accountId == null) {
                ctx.result("Invalid link code for " + capitalisedService + ".");
                ctx.status(422);
                return;
            }

            if (body.service.equals(LinkCode.Service.MODRINTH.serializedName())) {
				handleModrinth(ctx, connection, body.discordId, accountId, capitalisedService);
				return;
			} else if (body.service.equals(LinkCode.Service.MINECRAFT.serializedName())) {
				handleMinecraft(ctx, connection, body.discordId, accountId, capitalisedService);
				return;
			}
			ctx.result("Invalid link code service '" + capitalisedService + "'.");
			ctx.status(422);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
    }

	private static void handleModrinth(Context ctx,
									   Connection connection,
									   String discordId,
									   String accountId,
									   String capitalisedService) throws SQLException {
		try (var accountCheckStatement = connection.prepareStatement("SELECT 1 FROM users WHERE modrinth_id = ?");
			 var userCheckStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ? AND modrinth_id IS NOT NULL");
			 var insertStatement = connection.prepareStatement("UPDATE users SET modrinth_id = ? WHERE discord_id = ?")) {
			accountCheckStatement.setString(1, accountId);
			ResultSet accountCheckResult = accountCheckStatement.executeQuery();
			if (accountCheckResult.isBeforeFirst() && accountCheckResult.getBoolean(1)) {
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

			ctx.result("Successfully linked " + capitalisedService + " account to Mod Garden account associated with Discord ID '" + discordId + "'.");
			ctx.status(201);
		}
	}

	private static void handleMinecraft(Context ctx,
									   Connection connection,
									   String discordId,
									   String uuid,
									   String capitalisedService) throws SQLException {
		try (var accountCheckStatement = connection.prepareStatement("SELECT 1 FROM users WHERE instr(minecraft_accounts, ?) > 0");
			 var insertStatement = connection.prepareStatement("UPDATE users SET minecraft_accounts = ? WHERE discord_id = ?")) {
			accountCheckStatement.setString(1, uuid);
			ResultSet accountCheckResult = accountCheckStatement.executeQuery();
			if (accountCheckResult.isBeforeFirst() && accountCheckResult.getBoolean(1)) {
				ctx.result("The specified " + capitalisedService + " account has already been linked to a Mod Garden account.");
				ctx.status(422);
				return;
			}

			User user = User.query(discordId, "discord");
			if (user == null) {
				ctx.result("Could not find user from Discord ID '" + discordId + "'.");
				ctx.status(422);
				return;
			}

			List<UUID> uuids = new ArrayList<>(user.minecraftAccounts());
			uuids.add(new UUID(
					new BigInteger(uuid.substring(0, 16), 16).longValue(),
					new BigInteger(uuid.substring(16), 16).longValue()
			));

			var dataResult = ExtraCodecs.UUID_CODEC.listOf().encodeStart(JsonOps.INSTANCE, uuids);

			if (!dataResult.hasResultOrPartial()) {
				ModGardenBackend.LOG.error("Failed to create Minecraft account data. {}", dataResult.error().orElseThrow().message());
				ctx.result("Failed to create Minecraft account data.");
				ctx.status(500);
				return;
			}

			accountCheckStatement.setString(1, dataResult.getOrThrow().toString());
			accountCheckStatement.setString(2, discordId);
			insertStatement.execute();

			ctx.result("Successfully linked " + capitalisedService + " account to Mod Garden account associated with Discord ID '" + discordId + "'.");
			ctx.status(201);
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
