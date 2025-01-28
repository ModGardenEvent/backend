package net.modgarden.backend.util;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.profile.User;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static net.modgarden.backend.data.profile.User.*;

public class DatabaseAccess {
	private static Connection connection;

	protected static Connection getConnection() {
		try {
			if (connection == null || connection.isClosed()) {
				ModGardenBackend.LOG.info("Creating new database connection.");
				connection = DriverManager.getConnection("jdbc:sqlite:database.db");
			}
		} catch (SQLException e) {
			ModGardenBackend.LOG.error("Failed to get read-only database connection: {}", e.getMessage());
		}

		return connection;
	}


	public static User userFromModrinthUsername(String modrinthUsername) {
		try {
			String usernameToId = getUserModrinthId(modrinthUsername);
			if (usernameToId == null)
				return null;
			return userFromModrinthId(usernameToId);
		} catch (IOException | InterruptedException ex) {
			return null;
		}
	}

	public static User userFromModrinthId(String modrinthId) {
		return baseUserQuery("modrinth_id = ?", modrinthId);
	}

	public static User userFromDiscordId(String discordId) {
		return baseUserQuery("discord_id = ?", discordId);
	}

	public static User userFromUsername(String username) {
		return baseUserQuery("username = ?", username);
	}

	public static User userFromId(String id) {
		return baseUserQuery("id = ?", id);
	}
	public static User userFromDiscordUsername(String discordUsername) {
		try {
			String usernameToId = getUserDiscordId(discordUsername);
			if (usernameToId == null)
				return null;
			return userFromDiscordId(usernameToId);
		} catch (IOException | InterruptedException ex) {
			return null;
		}
	}

	private static User baseUserQuery(String whereStatement, String id) {
		try {
			var connection = getConnection();
			var prepared = connection.prepareStatement(userSelectStatement(whereStatement));
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (!result.isBeforeFirst())
				return null;
			return User.CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
		} catch (IllegalStateException ex) {
			ModGardenBackend.LOG.error("Could not decode user. ", ex);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return null;
	}

	private static String userSelectStatement(String whereStatement) {
		return "SELECT " +
				"u.id, " +
				"u.username, " +
				"u.display_name, " +
				"u.discord_id, " +
				"u.modrinth_id, " +
				"u.created, " +
				"CASE " +
				"WHEN p.id NOT NULL THEN json_group_array(DISTINCT p.id) " +
				"ELSE json_array() " +
				"END AS projects, " +
				"CASE " +
				"WHEN e.id NOT NULL THEN json_group_array(DISTINCT e.id) " +
				"ELSE json_array() " +
				"END AS events, " +
				"CASE " +
				"WHEN ma.uuid NOT NULL THEN json_group_array(DISTINCT ma.uuid) " +
				"ELSE json_array() " +
				"END AS minecraft_accounts, " +
				"CASE " +
				"WHEN ai.award_id NOT NULL THEN json_group_array(DISTINCT json_object('award_id', ai.award_id, 'custom_data', ai.custom_data)) " +
				"ELSE json_array() " +
				"END AS awards " +
				"FROM " +
				"users u " +
				"LEFT JOIN " +
				"project_authors a ON u.id = a.user_id " +
				"LEFT JOIN " +
				"projects p ON p.id = a.project_id " +
				"LEFT JOIN " +
				"submissions s ON p.id = s.project_id " +
				"LEFT JOIN " +
				"events e ON s.event = e.id " +
				"LEFT JOIN " +
				"minecraft_accounts ma ON u.id = ma.user_id " +
				"LEFT JOIN " +
				"award_instances ai ON u.id = ai.awarded_to " +
				"WHERE " +
				"u." + whereStatement + " " +
				"GROUP BY " +
				"u.id, u.username, u.display_name, u.discord_id, u.modrinth_id, u.created";
	}
}
