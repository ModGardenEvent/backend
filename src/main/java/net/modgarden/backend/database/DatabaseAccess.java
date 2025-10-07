package net.modgarden.backend.database;

import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Permissions;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class DatabaseAccess {
	public Connection getDatabaseConnection() throws SQLException {
		return ModGardenBackend.createDatabaseConnection();
	}

	public HypertextResult<Permissions> getUserPermissions(String userId) throws SQLException {
		try (
				var connection = getDatabaseConnection();
				var userStatement = connection.prepareStatement("SELECT permissions FROM users WHERE id = ?");
		) {
			userStatement.setString(1, userId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				return new HypertextResult<>(404, "User does not exist.");
			}

			return new HypertextResult<>(new Permissions(resultSet.getLong("permissions")));
		}
	}

	public HypertextResult<Permissions> getProjectPermissions(String userId, String projectId) throws SQLException {
		try (
				var connection = getDatabaseConnection();
				var userStatement = connection.prepareStatement("SELECT permissions FROM project_roles WHERE user_id = ? AND project_id = ?");
		) {
			userStatement.setString(1, userId);
			userStatement.setString(2, projectId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				return new HypertextResult<>(404, "User does not have the specified project role.");
			}

			return new HypertextResult<>(new Permissions(resultSet.getLong("permissions")));
		}
	}
}
