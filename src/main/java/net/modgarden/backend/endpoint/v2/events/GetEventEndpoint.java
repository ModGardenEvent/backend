package net.modgarden.backend.endpoint.v2.events;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/events/{genre_id}/{event_id}")
public class GetEventEndpoint extends EventsEndpoint {
	public GetEventEndpoint() {
		super("{genre_id}/{event_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String genreSlug;
		String eventSlug;
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);

		switch (queryKey) {
		case SLUG -> {
			genreSlug = ctx.pathParam("genre_id");
			eventSlug = ctx.pathParam("event_id");
		}
		case ID -> {
			genreSlug = db.getGenreById(ctx.pathParam("genre_id")).slug();
			eventSlug = db.getEventSlug(genreSlug, ctx.pathParam("event_id"));
		}
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		ctx.json(db.getEvent(genreSlug, eventSlug));
		ctx.status(200);
	}
}
