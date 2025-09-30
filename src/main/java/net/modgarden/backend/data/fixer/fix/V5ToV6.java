package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.SQLException;

public class V5ToV6 extends DatabaseFix {
	public V5ToV6() {
		super(5);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		var statement = connection.createStatement();
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_keys (
			uuid BLOB PRIMARY KEY,
			salt TEXT NOT NULL,
			hash TEXT NOT NULL,
			expires INTEGER NOT NULL
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS credentials (
			user_id TEXT PRIMARY KEY,
			api_key_uuid BLOB UNIQUE
		)
		""");
		statement.executeBatch();
	}
}
