package net.modgarden.backend.endpoint.internal;

import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.Nullable;

/**
 * A kind of {@link Endpoint} that is used only internally.
 * <br><br>
 * Beware! These endpoints are for <b>internal team use only</b>!
 * Discussion of these endpoints in public spaces is heavily frowned upon.
 * Usage of these endpoints is also discouraged, as your project will break.
 * These endpoints may change at any time without notice.
 */
@EndpointPath("/internal")
public abstract class InternalEndpoint extends AuthorizedEndpoint {
	public InternalEndpoint(
			String path
	) {
		super("/internal/" + path, PermissionScope.USER);
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.ADMINISTRATOR);
	}
}
