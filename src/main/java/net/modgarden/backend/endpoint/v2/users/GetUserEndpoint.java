package net.modgarden.backend.endpoint.v2.users;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@EndpointMethod(GET)
@EndpointPath("/v2/users/{user_id}")
public class GetUserEndpoint extends UsersEndpoint {
	public GetUserEndpoint() {
		super("{user_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.ID);
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);
		String userId = ctx.pathParam("user_id").toLowerCase(Locale.ROOT);

		switch (queryKey) {
		case ID -> {
		}
		case USERNAME -> userId = db.getUserIdFromUsername(userId);
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		if (userId == null) {
			return;
		}

		switch (queryValue) {
		case VALUE -> ctx.json(db.getUserFromId(userId));
		case ID -> ctx.json(userId);
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
			return;
		}
		}

		ctx.status(200);
	}
}

