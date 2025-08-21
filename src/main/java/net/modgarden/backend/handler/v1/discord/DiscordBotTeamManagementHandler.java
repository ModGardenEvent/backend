package net.modgarden.backend.handler.v1.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.AuthUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBotTeamManagementHandler {
	public static void sendInvite(Context ctx) {
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

		InviteBody inviteBody = ctx.bodyAsClass(InviteBody.class);
		String role = inviteBody.role.toLowerCase(Locale.ROOT);

		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			if (!"author".equals(role) && !"builder".equals(role)) {
				ctx.result("Invalid role '" + role + "'.");
				ctx.status(400);
				return;
			}
			var checkAuthorStatement = connection.prepareStatement(
					"SELECT user_id FROM project_authors WHERE project_id = ? AND user_id = ?");
			checkAuthorStatement.setString(1, inviteBody.projectId);
			checkAuthorStatement.setString(2, inviteBody.userId);
			var checkAuthorResult = checkAuthorStatement.executeQuery();
			if (checkAuthorResult.next()) {
				ctx.result("User is already a member of the project as an author.");
				ctx.status(200);
				return;
			}
			if ("builder".equals(role)) {
				var checkBuilderStatement = connection.prepareStatement(
						"SELECT user_id FROM project_builders WHERE project_id = ? AND user_id = ?");
				checkBuilderStatement.setString(1, inviteBody.projectId);
				checkBuilderStatement.setString(2, inviteBody.userId);
				var checkBuilderResult = checkBuilderStatement.executeQuery();
				if (checkBuilderResult.next()) {
					ctx.result("User is already a member of the project as a builder.");
					ctx.status(200);
					return;
				}
			}


			var deleteDifferentTeamRoleInvitationsStatement = connection.prepareStatement(
					"""
					UPDATE team_invites
						SET expires = ?
					WHERE
						project_id = ?
						AND
						user_id = ?
						AND
						role != ?
					""");
			deleteDifferentTeamRoleInvitationsStatement.setLong(1, getInviteExpirationTime());
			deleteDifferentTeamRoleInvitationsStatement.setString(2, inviteBody.projectId);
			deleteDifferentTeamRoleInvitationsStatement.setString(3, inviteBody.userId);
			deleteDifferentTeamRoleInvitationsStatement.setString(4, inviteBody.role);
			deleteDifferentTeamRoleInvitationsStatement.execute();

			var updateTeamExpiresStatement = connection.prepareStatement(
					"""
					UPDATE team_invites
						SET expires = ?
					WHERE
						project_id = ?
						AND
						user_id = ?
					""");
			updateTeamExpiresStatement.setLong(1, getInviteExpirationTime());
			updateTeamExpiresStatement.setString(2, inviteBody.projectId);
			updateTeamExpiresStatement.setString(3, inviteBody.userId);
			int expiryCount = updateTeamExpiresStatement.executeUpdate();
			if (expiryCount > 0) {
				ctx.result("Updated expiry for project invitation to a later time.");
				ctx.status(201);
				return;
			}
			var code = AuthUtil.generateRandomToken();
			var insertTeamInviteStatement = connection.prepareStatement(
					"INSERT INTO team_invites (code, project_id, user_id, expires, role) VALUES (?, ?, ?, ?, ?)");
			insertTeamInviteStatement.setString(1, code);
			insertTeamInviteStatement.setString(2, inviteBody.projectId);
			insertTeamInviteStatement.setString(3, inviteBody.userId);
			insertTeamInviteStatement.setLong(4, getInviteExpirationTime());
			insertTeamInviteStatement.setString(5, role);
			insertTeamInviteStatement.execute();
			ctx.result(code);
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public static void acceptInvite(Context ctx) {
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

		AcceptInviteBody acceptInviteBody = ctx.bodyAsClass(AcceptInviteBody.class);
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var checkInviteStatement = connection.prepareStatement(
					"SELECT * FROM team_invites WHERE code = ?");
			checkInviteStatement.setString(1, acceptInviteBody.inviteCode);
			var checkInviteResult = checkInviteStatement.executeQuery();
			if (!checkInviteResult.next()) {
				ctx.result("Invalid Team Invite Code.");
				ctx.status(400);
				return;
			}
			var projectId = checkInviteResult.getString("project_id");
			var userId = checkInviteResult.getString("user_id");
			var role = checkInviteResult.getString("role");

			var deleteInviteStatement = connection.prepareStatement(
					"DELETE FROM team_invites WHERE code = ?");
			deleteInviteStatement.setString(1, acceptInviteBody.inviteCode);
			deleteInviteStatement.execute();

			if (Objects.equals(role, "author")) {
				var deleteBuilderStatement = connection.prepareStatement(
						"DELETE FROM project_builders WHERE project_id = ? AND user_id = ?"
				);
				deleteBuilderStatement.setString(1, projectId);
				deleteBuilderStatement.setString(2, userId);
				deleteBuilderStatement.execute();

				var insertAuthorStatement = connection.prepareStatement(
						"INSERT INTO project_authors (project_id, user_id) VALUES (?, ?)");
				insertAuthorStatement.setString(1, projectId);
				insertAuthorStatement.setString(2, userId);
				insertAuthorStatement.execute();
				ctx.result("Successfully joined project as " + role + ".");
				ctx.status(201);
			} else if (Objects.equals(role, "builder")) {
				var insertBuilderStatement = connection.prepareStatement(
						"INSERT INTO project_builders (project_id, user_id) VALUES (?, ?)");
				insertBuilderStatement.setString(1, projectId);
				insertBuilderStatement.setString(2, userId);
				insertBuilderStatement.execute();
				ctx.result("Successfully joined project as " + role + ".");
				ctx.status(201);
			} else {
				ctx.result("Invalid role in invite.");
				ctx.status(500);
			}
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public static void declineInvite(Context ctx) {
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

		DeclineInviteBody declineInviteBody = ctx.bodyAsClass(DeclineInviteBody.class);
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var checkInviteStatement = connection.prepareStatement(
					"SELECT * FROM team_invites WHERE code = ?");
			checkInviteStatement.setString(1, declineInviteBody.inviteCode);
			var checkInviteResult = checkInviteStatement.executeQuery();
			if (!checkInviteResult.next()) {
				ctx.result("Invalid Team Invite Code.");
				ctx.status(400);
				return;
			}
			var deleteInviteStatement = connection.prepareStatement(
					"DELETE FROM team_invites WHERE code = ?");
			deleteInviteStatement.setString(1, declineInviteBody.inviteCode);
			deleteInviteStatement.execute();

			ctx.result("Successfully declined invite to project.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public static void removeMember(Context ctx) {
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
		// TODO: Note for rewrite, this is cursed because there are two different role tables, should be unified in the future
		RemoveMemberBody removeMemberBody = ctx.bodyAsClass(RemoveMemberBody.class);
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var deleteAuthorStatement = connection.prepareStatement(
					"DELETE FROM project_authors WHERE project_id = ? AND user_id = ?");
			deleteAuthorStatement.setString(1, removeMemberBody.projectId);
			deleteAuthorStatement.setString(2, removeMemberBody.userId);
			int authorRowsAffected = deleteAuthorStatement.executeUpdate();
			var deleteBuilderStatement = connection.prepareStatement(
					"DELETE FROM project_builders WHERE project_id = ? AND user_id = ?");
			deleteBuilderStatement.setString(1, removeMemberBody.projectId);
			deleteBuilderStatement.setString(2, removeMemberBody.userId);
			int builderRowsAffected = deleteBuilderStatement.executeUpdate();
			if (authorRowsAffected == 0 && builderRowsAffected == 0) {
				ctx.result("User is not a member of the project.");
				ctx.status(400);
				return;
			}

			ctx.result("Successfully removed member from project.");
			ctx.status(201);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
			ctx.result("Internal Error.");
			ctx.status(500);
		}
	}

	public record InviteBody(String projectId, String userId, String role) {
		public static final Codec<InviteBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("project_id").forGetter(InviteBody::projectId),
				Codec.STRING.fieldOf("user_id").forGetter(InviteBody::userId),
				Codec.STRING.fieldOf("role").forGetter(InviteBody::role)
		).apply(inst, InviteBody::new));
	}

	public record AcceptInviteBody(String inviteCode) {
		public static final Codec<AcceptInviteBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("invite_code").forGetter(AcceptInviteBody::inviteCode)
		).apply(inst, AcceptInviteBody::new));
	}

	public record DeclineInviteBody(String inviteCode) {
		public static final Codec<DeclineInviteBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("invite_code").forGetter(DeclineInviteBody::inviteCode)
		).apply(inst, DeclineInviteBody::new));
	}

	public record RemoveMemberBody(String projectId, String userId) {
		public static final Codec<RemoveMemberBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("project_id").forGetter(RemoveMemberBody::projectId),
				Codec.STRING.fieldOf("user_id").forGetter(RemoveMemberBody::userId)
		).apply(inst, RemoveMemberBody::new));
	}


	public static long getInviteExpirationTime() {
		return (long) (Math.floor((double) (System.currentTimeMillis() + 86400000) / 86400000) * 86400000); // 24 hours later, rounded to the nearest day.
	}

	public static void clearInvitesEachDay() {
		new Thread(() -> {
			try (ScheduledExecutorService executor = Executors.newScheduledThreadPool(1)) {
				long scheduleTime = (long) (Math.floor((double) (System.currentTimeMillis() + 86400000) / 86400000) * 86400000) - System.currentTimeMillis();
				executor.schedule(() -> {
					clearTokens();
					executor.schedule(AuthUtil::getTokenExpirationTime, 86400000, TimeUnit.MILLISECONDS);
				}, scheduleTime, TimeUnit.MILLISECONDS);
			}
		}).start();
	}

	private static void clearTokens() {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement statement = connection.prepareStatement("DELETE FROM team_invites WHERE expires <= ?")) {
			statement.setLong(1, System.currentTimeMillis());
			int total = statement.executeUpdate();
			ModGardenBackend.LOG.debug("Cleared {} team invite tokens.", total);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Failed to clear team invite tokens from database.");
		}
	}
}
