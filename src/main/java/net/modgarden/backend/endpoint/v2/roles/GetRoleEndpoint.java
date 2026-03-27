package net.modgarden.backend.endpoint.v2.roles;

import io.javalin.http.Context;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
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
	public void onRequest(@NotNull Context ctx) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		String roleId = ctx.pathParam("role_id").toLowerCase(Locale.ROOT);

		ctx.json(db.getUserRoleFromId(roleId));
		ctx.status(200);
	}
}

