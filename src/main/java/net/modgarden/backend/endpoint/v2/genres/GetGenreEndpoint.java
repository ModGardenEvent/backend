package net.modgarden.backend.endpoint.v2.genres;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/genres/{genre_id}")
public class GetGenreEndpoint extends GenresEndpoint {
	public GetGenreEndpoint() {
		super("{genre_id}");
	}

	@Override
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String id = ctx.pathParam("genre_id");
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);

		switch (queryKey) {
		case ID -> ctx.json(db.getGenreById(id));
		case SLUG -> ctx.json(db.getGenreBySlug(id));
		default -> {
			this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			return;
		}
		}

		ctx.status(200);
	}
}
