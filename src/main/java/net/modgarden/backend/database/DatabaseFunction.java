package net.modgarden.backend.database;

import java.sql.Connection;
import java.sql.SQLException;

import org.sqlite.Function;

public abstract class DatabaseFunction extends Function {
	protected DatabaseFunction() {}

	protected abstract String getName();

	public void create(Connection connection) throws SQLException {
		Function.create(connection, getName(), this);
	}
}
