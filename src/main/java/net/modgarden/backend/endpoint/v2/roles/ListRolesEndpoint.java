package net.modgarden.backend.endpoint.v2.roles;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.GET;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(GET)
@EndpointPath("/v2/roles")
public class ListRolesEndpoint extends AuthorizedRolesEndpoint {
	public ListRolesEndpoint() {
		super();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.LIST_USER_INFO);
	}

	@Override
	public Response onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);

		return Response.ok(switch (queryValue) {
		case VALUE -> db.getUserRoles();
		case ID -> db.getUserRoleIds();
		default -> throw this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
		});
	}
}
