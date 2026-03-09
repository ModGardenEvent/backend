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
		String genreSlug;
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		switch (queryKey) {
		case SLUG -> genreSlug = ctx.pathParam("genre_id");
		case ID -> genreSlug = db.getGenreById(ctx.pathParam("genre_id")).slug();
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		switch (queryValue) {
		case VALUE -> ctx.json(db.getEvents());
		case ID -> ctx.json(db.getEventIdsFromGenreSlug(genreSlug));
		case SLUG -> ctx.json(db.getEventSlugs(genreSlug));
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
			return;
		}
		}

		ctx.status(200);
	}
}
