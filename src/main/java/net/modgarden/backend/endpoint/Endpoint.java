package net.modgarden.backend.endpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.modgarden.backend.HypertextResult;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

// witnesses would be *real* nice here. *sigh*
@EndpointPath("/")
public abstract class Endpoint implements Handler {
	public static final String SAFE_URL_REGEX = "[a-zA-Z0-9!@$()`.+,_\"-]+";

	private final String path;
	private final DatabaseAccess databaseAccess = new DatabaseAccess();

	public Endpoint(int version, String path) {
		this.path = "/v" + version + "/" + path;
	}

	// for our other types of Endpoints that don't follow the /vN/<path> convention
	Endpoint(String path) {
		this.path = path;
	}

	@Override
	public final void handle(@NotNull Context ctx) throws Exception {
		// validate all path params
		for (String pathParam : ctx.pathParamMap().values()) {
			if (!pathParam.matches(SAFE_URL_REGEX)) {
				ctx.result("Illegal characters in path '" + pathParam + "'.");
				ctx.status(422);
				return;
			}
		}

		try {
			this.onRequest(ctx);
		} catch (NotFoundException npe) {
			ctx.status(404);
			ctx.result(npe.getMessage());
		}
	}

	public abstract void onRequest(@NotNull Context ctx) throws Exception;

	public String getPath() {
		return path;
	}

	protected DatabaseAccess getDatabaseAccess() {
		return databaseAccess;
	}

	protected Connection getDatabaseConnection() throws SQLException {
		return this.getDatabaseAccess().getDatabaseConnection();
	}

	protected void invalidBody(Context ctx, String message) {
		ctx.status(400);
		ctx.result("Invalid body: " + message);
	}

	protected <T> HypertextResult<T> decodeBody(Context ctx, Codec<T> codec) {
		DataResult<Pair<T, JsonElement>> result = codec.decode(
				JsonOps.INSTANCE, JsonParser.parseString(ctx.body()));

		if (result.isError()) {
			//noinspection OptionalGetWithoutIsPresent
			this.invalidBody(ctx, result.error().get().message());
			return new HypertextResult<>(ctx.statusCode(), ctx.result());
		}

		T bodyResult;
		try {
			bodyResult = result.getOrThrow().getFirst();
		} catch (IllegalStateException e) {
			this.invalidBody(ctx, e.getMessage());
			return new HypertextResult<>(ctx.statusCode(), ctx.result());
		}

		return new HypertextResult<>(bodyResult);
	}
}
