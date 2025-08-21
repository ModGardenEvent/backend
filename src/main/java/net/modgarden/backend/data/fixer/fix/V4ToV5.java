package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.SQLException;

public class V4ToV5 extends DatabaseFix {
	public V4ToV5() {
		super(4);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		var statement = connection.createStatement();
		statement.addBatch("CREATE TABLE IF NOT EXISTS team_invites (" +
				"code TEXT NOT NULL," +
				"project_id TEXT NOT NULL," +
				"user_id TEXT NOT NULL," +
				"expires INTEGER NOT NULL," +
				"role TEXT NOT NULL CHECK (role IN ('author', 'builder'))," +
				"FOREIGN KEY (project_id) REFERENCES projects(id)," +
				"FOREIGN KEY (user_id) REFERENCES users(id)," +
				"PRIMARY KEY (code)" +
				")");
		statement.executeBatch();
	}
}
