package net.modgarden.backend.endpoint;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

// witnesses would be *real* nice here. *sigh*
public abstract class Endpoint implements Handler {
	private final String path;

	public Endpoint(String path) {
		this.path = path;
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
	}

	public String getPath() {
		return path;
	}

	protected Connection getDatabaseConnection() throws SQLException {
		return ModGardenBackend.createDatabaseConnection();
	}
}
