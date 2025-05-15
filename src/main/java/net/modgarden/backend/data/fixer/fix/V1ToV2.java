package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class V1ToV2 extends DatabaseFix {
	public V1ToV2() {
		super(1);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		Statement addDiscordRoleStatement = connection.createStatement();
		addDiscordRoleStatement.execute("ALTER TABLE events ADD COLUMN discord_role_id TEXT NOT NULL");

		Statement dropDescriptionStatement = connection.createStatement();
		dropDescriptionStatement.execute("ALTER TABLE events DROP COLUMN description");

		Statement dropLoaderVersionStatement = connection.createStatement();
		dropLoaderVersionStatement.execute("ALTER TABLE events DROP COLUMN loader_version");
	}
}
