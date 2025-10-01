package net.modgarden.backend.data.fixer;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

public abstract class DatabaseFix {
	private final int versionToFixFrom;

	public DatabaseFix(int versionToFixFrom) {
		this.versionToFixFrom = versionToFixFrom;
	}

	/// Data-fix the database.
	///
	/// @param connection a common connection between datafixers.
	/// @return a consumer with a fresh, datafixer-specific connection useful only for dropping tables.
	public abstract @Nullable Consumer<Connection> fix(Connection connection) throws SQLException;

	protected Consumer<Connection> fixInternal(Connection connection, int currentSchemaVersion) throws SQLException {
		if (versionToFixFrom < currentSchemaVersion)
			return null;
		return fix(connection);
	}
}
