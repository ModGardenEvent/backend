package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class V2ToV3 extends DatabaseFix {
	public V2ToV3() {
		super(2);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		Statement discordRoleIdToNotNullStatement = connection.createStatement();
		discordRoleIdToNotNullStatement.addBatch("ALTER TABLE events RENAME COLUMN discord_role_id TO temp");
		discordRoleIdToNotNullStatement.addBatch("ALTER TABLE events ADD COLUMN discord_role_id TEXT");
		discordRoleIdToNotNullStatement.addBatch("INSERT INTO events(discord_role_id) SELECT temp FROM events");
		discordRoleIdToNotNullStatement.addBatch("ALTER TABLE events DROP COLUMN temp");
		discordRoleIdToNotNullStatement.executeBatch();

		Statement addRegistrationTimeStatement = connection.createStatement();
		addRegistrationTimeStatement.execute("ALTER TABLE events ADD COLUMN registration_time INTEGER NOT NULL");
	}
}
