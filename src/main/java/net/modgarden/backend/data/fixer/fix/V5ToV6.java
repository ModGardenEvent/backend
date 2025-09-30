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
			user_id TEXT NOT NULL,
			salt BLOB NOT NULL,
			hash BLOB UNIQUE NOT NULL,
			expires INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS passwords (
			user_id TEXT NOT NULL,
			salt BLOB NOT NULL,
			hash BLOB NOT NULL,
			last_updated INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (user_id)
		)
		""");
		statement.executeBatch();
	}
}
