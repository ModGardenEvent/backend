package net.modgarden.backend.endpoint.v2.events;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/events/{genre_id}/{event_id}")
public class GetEventEndpoint extends EventsEndpoint {
	public GetEventEndpoint() {
		super("{genre_id}/{event_id}");
	}

	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String genreSlug;
		String eventSlug;
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		switch (queryKey) {
		case SLUG -> {
			genreSlug = ctx.pathParam("genre_id");
			eventSlug = ctx.pathParam("event_id");
		}
		case ID -> {
			genreSlug = db.getGenreSlug(ctx.pathParam("genre_id"));
			eventSlug = db.getEventSlug(ctx.pathParam("genre_id"), ctx.pathParam("event_id"));
		}
		default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
		}

		return Response.ok(switch (queryValue) {
			case VALUE -> db.getEventBySlug(genreSlug, eventSlug);
			case ID -> db.getEventId(genreSlug, eventSlug);
			case SLUG -> eventSlug;
			default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
		});
	}
}
