package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(GET)
@EndpointPath("/v2/projects/{id}")
public class GetProjectEndpoint extends Endpoint {
	public GetProjectEndpoint() {
		super(2, "projects/{id}");
	}

	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.PROJECT_ID);
		String id = ctx.pathParam("id");
		String projectId;
		DatabaseAccess db = DatabaseAccess.get();

		switch (queryKey) {
		case MOD_ID -> {
			projectId = db.getLatestProjectIdFromModId(id);

			if (projectId == null) {
				throw new NotFoundException("Could not find project from mod ID '" + id + "'");
			}
		}
		case PROJECT_ID -> {
			projectId = id;

			if (!db.projectExists(id)) {
				throw new NotFoundException("Could not find project from id '" + id + "'");
			}
		}
		default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
		}

		return Response.ok(db.getProjectFromId(projectId));
	}
}
