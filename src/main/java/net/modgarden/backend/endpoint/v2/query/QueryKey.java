package net.modgarden.backend.endpoint.v2.query;

import java.util.Locale;

import io.javalin.http.Context;

/// An enum representing what type of key to query a value by.
public enum QueryKey {
	MOD_ID,
	PROJECT_ID,
	SUBMISSION_ID,
	SLUG,
	USERNAME,
	UNDEFINED;

	public static QueryKey fromQuery(Context ctx, QueryKey defaultKey) {
		QueryKey queryKey = fromQuery(ctx);

		if (queryKey.equals(UNDEFINED)) {
			return defaultKey;
		} else {
			return queryKey;
		}
	}

	public static QueryKey fromQuery(Context ctx) {
		String param = ctx.queryParam(QueryParameterType.get(QueryKey.class).toString());

		if (param == null) {
			return UNDEFINED;
		}

		return QueryKey.valueOf(QueryKey.class, param.toUpperCase(Locale.ROOT));
	}
}
