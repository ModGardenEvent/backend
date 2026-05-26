package net.modgarden.backend.endpoint.v2.genres;

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
@EndpointPath("/v2/genres/{genre_id}")
public class GetGenreEndpoint extends GenresEndpoint {
	public GetGenreEndpoint() {
		super("{genre_id}");
	}

	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String id = ctx.pathParam("genre_id");
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.SLUG);
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		return Response.ok(switch (queryValue) {
			case VALUE -> switch (queryKey) {
				case ID -> db.getGenreById(id);
				case SLUG -> db.getGenreBySlug(id);
				default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			};
			case ID -> switch (queryKey) {
				case ID -> id;
				case SLUG -> db.getGenreId(id);
				default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			};
			case SLUG -> switch (queryKey) {
				case ID -> db.getGenreSlug(id);
				case SLUG -> id;
				default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
			};
			default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
		});
	}
}
