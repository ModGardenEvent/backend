package net.modgarden.backend.endpoint.v2.query;

import java.util.Locale;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.NotFoundException;

/// An enum representing what type of value to return from a query.
public enum QueryValue {
	VALUE,
	ID,
	SLUG,
	UNDEFINED;

	public static QueryValue fromQuery(Context ctx, QueryValue defaultValue) throws HypertextException {
		QueryValue queryValue = fromQuery(ctx);

		if (queryValue.equals(UNDEFINED)) {
			return defaultValue;
		} else {
			return queryValue;
		}
	}

	public static QueryValue fromQuery(Context ctx) throws HypertextException {
		String param = ctx.queryParam(QueryParameterType.get(QueryValue.class).toString());

		if (param == null) {
			return UNDEFINED;
		}
		try {
			return QueryValue.valueOf(QueryValue.class, param.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new NotFoundException("Query key context '" + param + "' does not exist");
		}
	}
}
