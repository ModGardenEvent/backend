package net.modgarden.backend.endpoint.v2.roles;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryKey;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

@EndpointMethod(GET)
@EndpointPath("/v2/roles/{role_id}")
public class GetRoleEndpoint extends RolesEndpoint {
	public GetRoleEndpoint() {
		super("{role_id}");
	}

	@Override
	public Response onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryKey queryKey = QueryKey.fromQuery(ctx, QueryKey.ID);
		String roleId = ctx.pathParam("role_id").toLowerCase(Locale.ROOT);


		switch (queryKey) {
			case ID -> {
			}
			case INTEGRATION_DISCORD -> roleId = db.getUserRoleIdFromDiscordRoleId(roleId);
			default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryKey.class));
		}

		return Response.ok(db.getUserRoleFromId(roleId));
	}
}

