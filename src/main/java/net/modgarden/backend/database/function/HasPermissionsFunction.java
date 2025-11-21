package net.modgarden.backend.database.function;

import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseFunction;

import java.sql.SQLException;

public class HasPermissionsFunction extends DatabaseFunction {
	public static final HasPermissionsFunction INSTANCE = new HasPermissionsFunction();

	protected HasPermissionsFunction() {}

	@Override
	protected void xFunc() throws SQLException {
		Permissions permissions = new Permissions(this.value_long(0));
		Permissions requiredPermissions = new Permissions(this.value_long(1));
		this.result(permissions.hasPermissions(requiredPermissions) ? 1 : 0);
	}

	@Override
	protected String getName() {
		return "has_permissions";
	}
}
