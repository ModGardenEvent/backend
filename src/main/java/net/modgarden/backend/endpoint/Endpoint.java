package net.modgarden.backend.endpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

// witnesses would be *real* nice here. *sigh*
@EndpointPath("/")
public abstract class Endpoint implements Handler {
	// *Please* don't touch this. In fact, don't use Regex without using https://regex101.com
	// and manually testing your change.
	public static final String SAFE_URL_REGEX = "^[a-zA-Z0-9!@$()`.+,_\"-]*$";

	private final String path;

	public Endpoint(int version, String path) {
		this.path = "/v" + version + "/" + path;
	}

	// for our other types of Endpoints that don't follow the /vN/<path> convention
	Endpoint(String path) {
		this.path = path;
	}

	@Override
	public final void handle(@NotNull Context ctx) throws Exception {
		ScopedValue.Carrier carrier = DatabaseAccess.bind();

		// validate all path params
		for (String pathParam : ctx.pathParamMap().values()) {
			if (!pathParam.matches(SAFE_URL_REGEX)) {
				throw new HypertextException(422, "Illegal characters in path '" + pathParam + "'.");
			}
		}

		carrier.<Void, Exception>call(() -> {
			this.onRequest(ctx);
			DatabaseAccess.get().close();
			return null;
		});
	}

	// TODO: Version of onRequest that allows returning Object and throwing HypertextException
	public abstract void onRequest(@NotNull Context ctx) throws Exception;

	public String getPath() {
		return path;
	}

	protected void invalidBody(Context ctx, String message) {
		ctx.status(400);
		ctx.result("Invalid body: " + message);
	}

	protected void invalidQuery(Context ctx, QueryParameterType queryParameterType) {
		ctx.status(400);
		String value = ctx.queryParam(queryParameterType.toString());

		if (value != null) {
			ctx.result("Invalid query parameter ('" + queryParameterType + "'): " + value);
		} else {
			ctx.result("Missing query parameter '" + queryParameterType + "'");
		}
	}

	protected <T> T decodeBody(Context ctx, Codec<T> codec) throws HypertextException {
		DataResult<Pair<T, JsonElement>> result = codec.decode(
				JsonOps.INSTANCE, JsonParser.parseString(ctx.body()));

		if (result.isError()) {
			//noinspection OptionalGetWithoutIsPresent
			this.invalidBody(ctx, result.error().get().message());
			throw new HypertextException(ctx.statusCode(), ctx.result());
		}

		T bodyResult;
		try {
			bodyResult = result.getOrThrow().getFirst();
		} catch (IllegalStateException e) {
			this.invalidBody(ctx, e.getMessage());
			throw new HypertextException(ctx.statusCode(), ctx.result());
		}

		return bodyResult;
	}
}
