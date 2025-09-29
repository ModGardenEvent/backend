package net.modgarden.backend.endpoint.v2;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.AuthorizedEndpoint;
import org.jetbrains.annotations.NotNull;

public abstract class AuthEndpoint extends AuthorizedEndpoint {
	public AuthEndpoint(String path) {
		super("auth/" + path);
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		super.handle(ctx);
	}
}
