package net.modgarden.backend.data.fixer;

import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class DatabaseFix {
	private final int versionToFixFrom;

	public DatabaseFix(int versionToFixFrom) {
		this.versionToFixFrom = versionToFixFrom;
	}

	public abstract void fix(Connection connection) throws SQLException;

	protected void fixInternal(Connection connection) throws SQLException {
		if (versionToFixFrom >= ModGardenBackend.DATABASE_SCHEMA_VERSION)
			return;
		fix(connection);
	}
}
