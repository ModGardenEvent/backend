package net.modgarden.backend.endpoint.v2.users;

import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.v2.query.QueryParameterType;
import net.modgarden.backend.endpoint.v2.query.QueryValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListUsersEndpoint extends AuthorizedUsersEndpoint {
	public ListUsersEndpoint() {
		super();
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.LIST_USER_INFO);
	}

	@Override
	protected Response onRequest(
			@NotNull Context ctx,
			String userId,
			Permissions scopePermissions
	) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();
		QueryValue queryValue = QueryValue.fromQuery(ctx, QueryValue.VALUE);
		return Response.ok(switch (queryValue) {
		case VALUE -> db.getUsers();
		case ID -> db.getUserIds();
		default -> this.invalidQuery(ctx, QueryParameterType.get(QueryValue.class));
		});
	}
}
