package net.modgarden.backend.database;

import org.sqlite.Function;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class DatabaseFunction extends Function {
	protected DatabaseFunction() {}

	protected abstract String getName();

	public void create(Connection connection) throws SQLException {
		Function.create(connection, getName(), this);
	}
}
