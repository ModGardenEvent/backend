package net.modgarden.backend.endpoint.v2.query;

import io.javalin.http.Context;

/// An enum representing what type of value to return from a query.
public enum QueryValue {
	VALUE,
	ID,
	UNDEFINED;

	public static QueryValue fromQuery(Context ctx, QueryValue defaultValue) {
		QueryValue queryValue = fromQuery(ctx);

		if (queryValue.equals(UNDEFINED)) {
			return defaultValue;
		} else {
			return queryValue;
		}
	}

	public static QueryValue fromQuery(Context ctx) {
		String param = ctx.queryParam(QueryParameterType.get(QueryValue.class).toString());

		if (param == null) {
			return UNDEFINED;
		}

		return QueryValue.valueOf(QueryValue.class, param);
	}
}
