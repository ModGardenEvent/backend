package net.modgarden.backend.database.function;

import net.modgarden.backend.database.DatabaseFunction;

import java.sql.SQLException;
import java.time.Instant;

public class UnixMillisFunction extends DatabaseFunction {
	public static final UnixMillisFunction INSTANCE = new UnixMillisFunction();

	protected UnixMillisFunction() {}

	@Override
	protected void xFunc() throws SQLException {
		this.result(Instant.now().toEpochMilli());
	}

	@Override
	protected String getName() {
		return "unix_millis";
	}
}
