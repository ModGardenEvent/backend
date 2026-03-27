package net.modgarden.backend.endpoint.v2.query;

import java.util.Locale;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.NotFoundException;

/// An enum representing what type of key to query a value by.
public enum QueryKey {
	MOD_ID,
	PROJECT_ID,
	SUBMISSION_ID,
	ID,
	SLUG,
	USERNAME,
	UNDEFINED;

	public static QueryKey fromQuery(Context ctx, QueryKey defaultKey) throws HypertextException {
		QueryKey queryKey = fromQuery(ctx);

		if (queryKey.equals(UNDEFINED)) {
			return defaultKey;
		} else {
			return queryKey;
		}
	}

	public static QueryKey fromQuery(Context ctx) throws HypertextException {
		String param = ctx.queryParam(QueryParameterType.get(QueryKey.class).toString());

		if (param == null) {
			return UNDEFINED;
		}

		try {
			return QueryKey.valueOf(QueryKey.class, param.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new NotFoundException("Query key context '" + param + "' does not exist");
		}
	}
}
