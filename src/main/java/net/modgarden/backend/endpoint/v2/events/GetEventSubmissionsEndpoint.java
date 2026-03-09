package net.modgarden.backend.endpoint.v2.events;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;

@EndpointPath("/v2/events/{genre_id}/{event_id}/submissions")
public class GetEventSubmissionsEndpoint extends EventsEndpoint {
	public GetEventSubmissionsEndpoint() {
		super("{genre_id}/{event_id}/submissions");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);
		String eventId;
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);

		switch (queryKey) {
		case ID -> {
			db.getGenreById(ctx.pathParam("genre_id")); // Ensure the genre exists
			eventId = ctx.pathParam("event_id");
		}
		case SLUG -> eventId = db.getEventId(db.getGenreBySlug(ctx.pathParam("genre_id")).slug(), ctx.pathParam("event_id"));
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		switch (queryValue) {
		case VALUE -> ctx.json(db.getEventSubmissions(eventId));
		case ID -> ctx.json(db.getEventSubmissionIds(eventId));
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
			return;
		}
		}

		ctx.status(200);
	}
}
