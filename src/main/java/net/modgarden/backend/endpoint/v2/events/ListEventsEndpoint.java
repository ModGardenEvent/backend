package net.modgarden.backend.endpoint.v2.events;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/events/{genre_id}")
public class ListEventsEndpoint extends EventsEndpoint {
	public ListEventsEndpoint() {
		super("{genre_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String genreId;
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		switch (queryKey) {
		case SLUG -> genreId = db.getGenreBySlug(ctx.pathParam("genre_id")).slug();
		case ID -> genreId = ctx.pathParam("genre_id");
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		switch (queryValue) {
		case VALUE -> ctx.json(db.getEvents());
		case ID -> ctx.json(db.getEventIds(genreId));
		case SLUG -> ctx.json(db.getEventSlugs(genreId));
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
			return;
		}
		}

		ctx.status(200);
	}
}
