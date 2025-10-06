package net.modgarden.backend.endpoint;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

// witnesses would be *real* nice here. *sigh*
@EndpointPath("/v2")
public abstract class Endpoint implements Handler {
	public static final String SAFE_URL_REGEX = "[a-zA-Z0-9!@$()`.+,_\"-]+";

	private final String path;

	public Endpoint(int version, String path) {
		this.path = "/v" + version + "/" + path;
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		// validate all path params
		for (String pathParam : ctx.pathParamMap().values()) {
			if (!pathParam.matches(SAFE_URL_REGEX)) {
				ctx.result("Illegal characters in path '" + pathParam + "'.");
				ctx.status(422);
				return;
			}
		}
	}

	public String getPath() {
		return path;
	}

	protected Connection getDatabaseConnection() throws SQLException {
		return ModGardenBackend.createDatabaseConnection();
	}

	protected void invalidBody(Context ctx, String message) {
		ctx.status(400);
		ctx.result("Invalid body: " + message);
	}
}
