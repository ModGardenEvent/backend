package net.modgarden.backend.data.fixer;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class DatabaseFix {
	private final int versionToFixFrom;

	public DatabaseFix(int versionToFixFrom) {
		this.versionToFixFrom = versionToFixFrom;
	}

	public abstract void fix(Connection connection) throws SQLException;

	protected void fixInternal(Connection connection, int currentSchemaVersion) throws SQLException {
		if (versionToFixFrom < currentSchemaVersion)
			return;
		fix(connection);
	}
}
