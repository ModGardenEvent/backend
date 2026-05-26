package net.modgarden.backend.endpoint.v2.genres;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/genres")
public class ListGenresEndpoint extends GenresEndpoint {
	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		return Response.ok(switch (queryValue) {
		case VALUE -> db.getGenres();
		case ID -> db.getGenreIds();
		case SLUG -> db.getGenreSlugs();
		default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
		});
	}
}
