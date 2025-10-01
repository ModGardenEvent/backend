package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

public class V3ToV4 extends DatabaseFix {
	public V3ToV4() {
		super(3);
	}

	@Override
	public @Nullable Consumer<Connection> fix(Connection connection) throws SQLException {
		Statement addFreezeTimeStatement = connection.createStatement();
		addFreezeTimeStatement.execute("ALTER TABLE events ADD COLUMN freeze_time INTEGER NOT NULL");
		return null;
	}
}
