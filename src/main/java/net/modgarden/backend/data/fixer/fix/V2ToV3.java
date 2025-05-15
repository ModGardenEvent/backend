package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class V2ToV3 extends DatabaseFix {
	public V2ToV3() {
		super(2);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		PreparedStatement renameOldRoleIdToTempValueStatement = connection.prepareStatement("ALTER TABLE events RENAME COLUMN discord_role_id TO temp");
		PreparedStatement addDiscordRoleIdStatement = connection.prepareStatement("ALTER TABLE events ADD COLUMN registration_time INTEGER NOT NULL");
		PreparedStatement insertDiscordRoleIdsStatement = connection.prepareStatement("INSERT INTO events(discord_role_id) SELECT temp FROM events");
		PreparedStatement dropTempStatement = connection.prepareStatement("ALTER TABLE events DROP COLUMN temp");

		PreparedStatement addRegistrationTimeStatement = connection.prepareStatement("ALTER TABLE events ADD COLUMN registration_time INTEGER NOT NULL");

		renameOldRoleIdToTempValueStatement.execute();
		addDiscordRoleIdStatement.execute();
		insertDiscordRoleIdsStatement.execute();
		dropTempStatement.execute();

		addRegistrationTimeStatement.execute();
	}
}
