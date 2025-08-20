package net.modgarden.backend.handler.v1.discord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.AuthUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

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

		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			if (Objects.equals(inviteBody.role, "author")) {
				var checkAuthorStatement = connection.prepareStatement(
						"SELECT user_id FROM project_authors WHERE project_id = ? AND user_id = ?");
				checkAuthorStatement.setString(1, inviteBody.projectId);
				checkAuthorStatement.setString(2, inviteBody.userId);
				var checkAuthorResult = checkAuthorStatement.executeQuery();
				if (checkAuthorResult.next()) {
					ctx.result("User already member of project as author.");
					ctx.status(400);
					return;
				}
			}
			if (Objects.equals(inviteBody.role, "builder")) {
				var checkBuilderStatement = connection.prepareStatement(
						"SELECT user_id FROM project_builders WHERE project_id = ? AND user_id = ?");
				checkBuilderStatement.setString(1, inviteBody.projectId);
				checkBuilderStatement.setString(2, inviteBody.userId);
				var checkBuilderResult = checkBuilderStatement.executeQuery();
				if (checkBuilderResult.next()) {
					ctx.result("User already member of project as builder.");
					ctx.status(400);
					return;
				}
			} else {
				ctx.result("Invalid role '" + inviteBody.role + "'.");
				ctx.status(400);
				return;
			}

			var code = AuthUtil.generateRandomToken();
			var insertTeamInviteStatement = connection.prepareStatement(
					"INSERT INTO team_invites (code, project_id, user_id, role) VALUES (?, ?, ?, ?)");
			insertTeamInviteStatement.setString(1, code);
			insertTeamInviteStatement.setString(2, inviteBody.projectId);
			insertTeamInviteStatement.setString(3, inviteBody.userId);
			insertTeamInviteStatement.setString(4, inviteBody.role);
			insertTeamInviteStatement.execute();
			ctx.result("Code:" + code);
			ctx.status(200);
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
				var insertAuthorStatement = connection.prepareStatement(
						"INSERT INTO project_authors (project_id, user_id) VALUES (?, ?)");
				insertAuthorStatement.setString(1, projectId);
				insertAuthorStatement.setString(2, userId);
				insertAuthorStatement.execute();
				ctx.result("Successfully joined project as " + role + ".");
				ctx.status(200);
			} else if (Objects.equals(role, "builder")) {
				var insertBuilderStatement = connection.prepareStatement(
						"INSERT INTO project_builders (project_id, user_id) VALUES (?, ?)");
				insertBuilderStatement.setString(1, projectId);
				insertBuilderStatement.setString(2, userId);
				insertBuilderStatement.execute();
				ctx.result("Successfully joined project as " + role + ".");
				ctx.status(200);
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

	public static void denyInvite(Context ctx) {
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

		DenyInviteBody denyInviteBody = ctx.bodyAsClass(DenyInviteBody.class);
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			var checkInviteStatement = connection.prepareStatement(
					"SELECT * FROM team_invites WHERE code = ?");
			checkInviteStatement.setString(1, denyInviteBody.inviteCode);
			var checkInviteResult = checkInviteStatement.executeQuery();
			if (!checkInviteResult.next()) {
				ctx.result("Invalid Team Invite Code.");
				ctx.status(400);
				return;
			}
			var deleteInviteStatement = connection.prepareStatement(
					"DELETE FROM team_invites WHERE code = ?");
			deleteInviteStatement.setString(1, denyInviteBody.inviteCode);
			deleteInviteStatement.execute();

			ctx.result("Successfully denied invite to project.");
			ctx.status(200);
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
			ctx.status(200);
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

	public record DenyInviteBody(String inviteCode) {
		public static final Codec<DenyInviteBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("invite_code").forGetter(DenyInviteBody::inviteCode)
		).apply(inst, DenyInviteBody::new));
	}

	public record RemoveMemberBody(String projectId, String userId) {
		public static final Codec<RemoveMemberBody> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("project_id").forGetter(RemoveMemberBody::projectId),
				Codec.STRING.fieldOf("user_id").forGetter(RemoveMemberBody::userId)
		).apply(inst, RemoveMemberBody::new));
	}
}
