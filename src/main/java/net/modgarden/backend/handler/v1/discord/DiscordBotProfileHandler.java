package net.modgarden.backend.handler.v1.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;

import java.sql.*;

public class DiscordBotProfileHandler {
    public static void modifyUsername(Context ctx) {
	    if (ModGardenBackend.isUnauthorized(ctx)) return;

	    PostBody body = ctx.bodyAsClass(PostBody.class);

        try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var selectStatement = connection.prepareStatement("SELECT username FROM users WHERE discord_id = ?");
			 var existingUserStatement = connection.prepareStatement("SELECT 1 FROM users WHERE username = ?");
             var updateStatement = connection.prepareStatement("UPDATE users SET username = ? WHERE discord_id = ?")) {
			selectStatement.setString(1, body.discordId);
			ResultSet oldUserResult = selectStatement.executeQuery();
			String oldUsername = oldUserResult.getString("username");


			if (body.value.length() < 3) {
				ctx.result("Username is too short.");
				ctx.status(422);
				return;
			}
			if (body.value.length() > 32) {
				ctx.result("Username is too long.");
				ctx.status(422);
				return;
			}
			if (!body.value.matches(User.USERNAME_REGEX)) {
				ctx.result("Username has invalid characters.");
				ctx.status(422);
				return;
			}
			if (body.value.equals(oldUsername)) {
				ctx.result("Your username is already '" + body.value + "'.");
				ctx.status(200);
				return;
			}

			existingUserStatement.setString(1, body.value);
			ResultSet existingUser = existingUserStatement.executeQuery();
			if (existingUser.getBoolean(1)) {
				ctx.result("Username '" + body.value + " ' has already been taken.");
				ctx.status(422);
				return;
			}

			updateStatement.setString(1, body.value);
			updateStatement.setString(2, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Changed Discord user {}'s Mod Garden username to {}.", body.discordId, body.value);
			ctx.result("Successfully changed your username from '" + oldUsername + "' to '" + body.value + "'.");
			ctx.status(201);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
    }

	public static void modifyDisplayName(Context ctx) {
		if (ModGardenBackend.isUnauthorized(ctx)) return;

		PostBody body = ctx.bodyAsClass(PostBody.class);

		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var selectStatement = connection.prepareStatement("SELECT display_name FROM users WHERE discord_id = ?");
			 var updateStatement = connection.prepareStatement("UPDATE users SET display_name = ? WHERE discord_id = ?")) {
			selectStatement.setString(1, body.discordId);
			ResultSet oldUserResult = selectStatement.executeQuery();
			String oldDisplayName = oldUserResult.getString("display_name");

			if (body.value.isBlank()) {
				ctx.result("Display name cannot be exclusively whitespace.");
				ctx.status(422);
				return;
			}
			if (body.value.length() > 32) {
				ctx.result("Display name is too long.");
				ctx.status(422);
				return;
			}

			updateStatement.setString(1, body.value);
			updateStatement.setString(2, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Changed Discord user {}'s Mod Garden display name to {}.", body.discordId, body.value);
			ctx.result("Successfully changed your display name from '" + oldDisplayName + "' to '" + body.value + "'.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public static void modifyPronouns(Context ctx) {
		if (ModGardenBackend.isUnauthorized(ctx)) return;

		PostBody body = ctx.bodyAsClass(PostBody.class);

		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var selectStatement = connection.prepareStatement("SELECT pronouns FROM users WHERE discord_id = ?");
			 var updateStatement = connection.prepareStatement("UPDATE users SET pronouns = ? WHERE discord_id = ?")) {
			selectStatement.setString(1, body.discordId);
			ResultSet oldUserResult = selectStatement.executeQuery();
			String oldPronouns = oldUserResult.getString("pronouns");

			if (body.value.isBlank()) {
				ctx.result("Pronouns cannot be exclusively whitespace.");
				ctx.status(422);
				return;
			}
			if (body.value.equals(oldPronouns)) {
				ctx.result("Your pronouns are already '" + body.value + "'.");
				ctx.status(200);
				return;
			}

			updateStatement.setString(1, body.value);
			updateStatement.setString(2, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Changed Discord user {}'s Mod Garden pronouns to {}.", body.discordId, body.value);
			ctx.result("Successfully changed your pronouns to '" + body.value + "'.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}


	public static void modifyAvatarUrl(Context ctx) {
		if (ModGardenBackend.isUnauthorized(ctx)) return;

		PostBody body = ctx.bodyAsClass(PostBody.class);

		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var updateStatement = connection.prepareStatement("UPDATE users SET avatar_url = ? WHERE discord_id = ?")) {

			if (!ModGardenBackend.SAFE_URL_REGEX.matches(body.value)) {
				ctx.result("Avatar URL has invalid characters.");
				ctx.status(422);
				return;
			}

			updateStatement.setString(1, body.value);
			updateStatement.setString(2, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Changed Discord user {}'s Mod Garden avatar to {}.", body.discordId, body.value);
			ctx.result("Successfully changed your avatar.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public record PostBody(String discordId, String value) {
		public static final Codec<PostBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(PostBody::discordId),
				Codec.STRING.fieldOf("value").forGetter(PostBody::value)
		).apply(inst, PostBody::new));
	}



	public static void removePronouns(Context ctx) {
		if (ModGardenBackend.isUnauthorized(ctx)) return;

		DeleteBody body = ctx.bodyAsClass(DeleteBody.class);

		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var selectStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ? AND pronouns IS NULL");
			 var updateStatement = connection.prepareStatement("UPDATE users SET pronouns = NULL WHERE discord_id = ?")) {
			selectStatement.setString(1, body.discordId);
			ResultSet selectSet = selectStatement.executeQuery();
			if (selectSet.getBoolean(1)) {
				ctx.result("You have no pronouns associated with your profile.");
				ctx.status(200);
				return;
			}

			updateStatement.setString(1, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Removed user {}'s Mod Garden pronouns.", body.discordId);
			ctx.result("Successfully removed your pronouns from your account.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}


	public static void removeAvatarUrl(Context ctx) {
		if (ModGardenBackend.isUnauthorized(ctx)) return;

		DeleteBody body = ctx.bodyAsClass(DeleteBody.class);

		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 var selectStatement = connection.prepareStatement("SELECT 1 FROM users WHERE discord_id = ? AND avatar_url IS NULL");
			 var updateStatement = connection.prepareStatement("UPDATE users SET avatar_url = NULL WHERE discord_id = ?")) {
			selectStatement.setString(1, body.discordId);
			ResultSet selectSet = selectStatement.executeQuery();
			if (selectSet.getBoolean(1)) {
				ctx.result("You have no avatar associated with your profile.");
				ctx.status(200);
				return;
			}

			updateStatement.setString(1, body.discordId);
			updateStatement.execute();

			ModGardenBackend.LOG.debug("Removed user {}'s Mod Garden avatar.", body.discordId);
			ctx.result("Successfully removed your avatar from your account.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public record DeleteBody(String discordId) {
		public static final Codec<DeleteBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(DeleteBody::discordId)
		).apply(inst, DeleteBody::new));
	}
}
