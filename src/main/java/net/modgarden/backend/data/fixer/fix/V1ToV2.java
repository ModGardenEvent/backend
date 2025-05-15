package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class V1ToV2 extends DatabaseFix {
	public V1ToV2() {
		super(1);
	}

	@Override
	public void fix(Connection connection) throws SQLException {
		PreparedStatement addDiscordRoleStatement = connection.prepareStatement("ALTER TABLE events ADD COLUMN discord_role_id TEXT NOT NULL");
		PreparedStatement dropDescriptionStatement = connection.prepareStatement("ALTER TABLE events DROP COLUMN description");
		PreparedStatement dropLoaderVersionStatement = connection.prepareStatement("ALTER TABLE events DROP COLUMN loader_version");

		addDiscordRoleStatement.execute();
		dropDescriptionStatement.execute();
		dropLoaderVersionStatement.execute();
	}
}
