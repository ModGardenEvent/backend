package net.modgarden.backend.endpoint;

import net.modgarden.backend.data.PermissionScope;

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
			String path,
			boolean hasBody
	) {
		super("/internal/" + path, PermissionScope.USER, hasBody);
	}
}
