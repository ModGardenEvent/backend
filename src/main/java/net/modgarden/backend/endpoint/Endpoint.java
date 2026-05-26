package net.modgarden.backend.endpoint;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.exception.BadRequestException;
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
				throw new BadRequestException("Illegal characters in path '" + pathParam + "'.");
			}
		}

		Response response = carrier.call(() -> {
			try {
				Response response1 = this.onRequest(ctx);
				DatabaseAccess.get().close();
				return response1;
			} catch (Exception e) {
				DatabaseAccess.get().close(); // ensure the db access is closed
				throw e;
			}
		});

		if (!response.getHeaders().isEmpty()) {
			for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
				ctx.header(entry.getKey(), entry.getValue());
			}
		}

		if (response.getBody() != null) {
			ctx.json(response.getBody());
		}

		ctx.status(response.getStatus());
	}

	// TODO: Version of onRequest that allows returning Object and throwing HypertextException
	public abstract Response onRequest(@NotNull Context ctx) throws Exception;

	public String getPath() {
		return path;
	}

	protected BadRequestException invalidBody(String message) {
		return new BadRequestException("Invalid body: " + message);
	}

	protected BadRequestException invalidQuery(Context ctx, QueryParameterType queryParameterType) {
		String value = ctx.queryParam(queryParameterType.toString());

		if (value != null) {
			return new BadRequestException("Invalid query parameter ('" + queryParameterType + "'): " + value);
		} else {
			return new BadRequestException("Missing query parameter '" + queryParameterType + "'");
		}
	}

	protected <T> T decodeBody(Context ctx, Codec<T> codec) throws HypertextException {
		DataResult<Pair<T, JsonElement>> result = codec.decode(
				JsonOps.INSTANCE, JsonParser.parseString(ctx.body()));

		if (result.isError()) {
			//noinspection OptionalGetWithoutIsPresent
			throw this.invalidBody(result.error().get().message());
		}

		T bodyResult;
		try {
			bodyResult = result.getOrThrow().getFirst();
		} catch (IllegalStateException e) {
			throw this.invalidBody(e.getMessage());
		}

		return bodyResult;
	}
}
